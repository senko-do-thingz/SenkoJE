package com.senkoje.infrastructure.tts;

import com.senkoje.domain.model.TargetLanguage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Nhà cung cấp TTS sử dụng bộ tổng hợp giọng nói sẵn có của hệ điều hành thông qua ProcessBuilder:
 * Windows (PowerShell + System.Speech), macOS (say), Linux (espeak-ng hoặc spd-say).
 * Mọi văn bản thẻ học đều được truyền qua tham số riêng biệt hoặc biến môi trường để tránh hoàn toàn lỗi chèn lệnh.
 */
public class SystemTtsProvider implements TtsService {

    public static final String PROVIDER_ID = "system";

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SPEAK_TIMEOUT = Duration.ofSeconds(15);

    // Kịch bản PowerShell hằng số đọc văn bản từ biến môi trường, tuyệt đối không nối chuỗi đầu vào người dùng
    private static final String WINDOWS_PROBE_SCRIPT =
            "Add-Type -AssemblyName System.Speech; "
                    + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$s.GetInstalledVoices().Count";

    private static final String WINDOWS_LIST_VOICES_SCRIPT =
            "Add-Type -AssemblyName System.Speech; "
                    + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$s.GetInstalledVoices() | ForEach-Object { "
                    + "$i = $_.VoiceInfo; Write-Output ($i.Name + '|' + $i.Culture.Name) }";

    private static final String WINDOWS_SPEAK_SCRIPT =
            "Add-Type -AssemblyName System.Speech; "
                    + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$rate = 0; [int]::TryParse($env:SENKOJE_TTS_RATE, [ref]$rate) | Out-Null; "
                    + "$s.Rate = [Math]::Max(-10, [Math]::Min(10, $rate)); "
                    + "if (-not [string]::IsNullOrWhiteSpace($env:SENKOJE_TTS_VOICE)) { "
                    + "  try { $s.SelectVoice($env:SENKOJE_TTS_VOICE) } catch { } "
                    + "} elseif (-not [string]::IsNullOrWhiteSpace($env:SENKOJE_TTS_LANG)) { "
                    + "  $match = $s.GetInstalledVoices() | Where-Object { "
                    + "    $_.VoiceInfo.Culture.Name -like ($env:SENKOJE_TTS_LANG + '*') "
                    + "  } | Select-Object -First 1; "
                    + "  if ($null -ne $match) { $s.SelectVoice($match.VoiceInfo.Name) } "
                    + "}; "
                    + "$s.Speak($env:SENKOJE_TTS_TEXT)";

    private final OsFamily osFamily;
    private final ProcessRunner processRunner;

    /**
     * Khởi tạo nhà cung cấp SystemTtsProvider với hệ điều hành hiện tại và DefaultProcessRunner.
     */
    public SystemTtsProvider() {
        this(System.getProperty("os.name", ""), new DefaultProcessRunner());
    }

    /**
     * Khởi tạo nhà cung cấp SystemTtsProvider với tên hệ điều hành và ProcessRunner tiêm vào phục vụ kiểm thử.
     *
     * @param osName tên hệ điều hành (ví dụ: Windows 11, Mac OS X, Linux)
     * @param processRunner bộ thực thi tiến trình
     */
    public SystemTtsProvider(String osName, ProcessRunner processRunner) {
        this.osFamily = detectOsFamily(osName);
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner must not be null");
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public TtsAvailability checkAvailability() {
        return switch (osFamily) {
            case WINDOWS -> checkWindowsAvailability();
            case MACOS -> checkMacAvailability();
            case LINUX -> checkLinuxAvailability();
            case OTHER -> new TtsAvailability(
                    false, PROVIDER_ID, "tts.status.unsupportedOs", "Unsupported operating system");
        };
    }

    @Override
    public List<TtsVoice> listVoices(TargetLanguage language) {
        Objects.requireNonNull(language, "language must not be null");
        return switch (osFamily) {
            case WINDOWS -> listWindowsVoices(language);
            case MACOS -> listMacVoices(language);
            case LINUX -> listLinuxVoices(language);
            case OTHER -> List.of();
        };
    }

    @Override
    public TtsPlaybackResult speak(TtsPlaybackRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.text().isBlank()) {
            return new TtsPlaybackResult(false, PROVIDER_ID, "tts.status.emptyText", "");
        }

        // Hủy âm thanh đang phát dở trước khi phát đoạn mới
        processRunner.cancelActive();

        return switch (osFamily) {
            case WINDOWS -> speakWindows(request);
            case MACOS -> speakMac(request);
            case LINUX -> speakLinux(request);
            case OTHER -> new TtsPlaybackResult(
                    false, PROVIDER_ID, "tts.status.unsupportedOs", "Unsupported operating system");
        };
    }

    @Override
    public void stop() {
        processRunner.cancelActive();
    }

    private TtsAvailability checkWindowsAvailability() {
        ProcessExecutionResult result = processRunner.run(
                List.of("powershell", "-NoProfile", "-NonInteractive", "-Command", WINDOWS_PROBE_SCRIPT),
                Map.of(),
                PROBE_TIMEOUT
        );
        if (result.isSuccess()) {
            return new TtsAvailability(true, PROVIDER_ID, "tts.status.ready", "Windows System.Speech");
        }
        return new TtsAvailability(false, PROVIDER_ID, "tts.status.systemUnavailable", result.stderr());
    }

    private TtsAvailability checkMacAvailability() {
        ProcessExecutionResult result = processRunner.run(
                List.of("say", "-v", "?"),
                Map.of(),
                PROBE_TIMEOUT
        );
        if (result.isSuccess()) {
            return new TtsAvailability(true, PROVIDER_ID, "tts.status.ready", "macOS say");
        }
        return new TtsAvailability(false, PROVIDER_ID, "tts.status.systemUnavailable", result.stderr());
    }

    private TtsAvailability checkLinuxAvailability() {
        ProcessExecutionResult espeak = processRunner.run(
                List.of("espeak-ng", "--version"),
                Map.of(),
                PROBE_TIMEOUT
        );
        if (espeak.isSuccess()) {
            return new TtsAvailability(true, PROVIDER_ID, "tts.status.ready", "Linux espeak-ng");
        }
        ProcessExecutionResult spdSay = processRunner.run(
                List.of("spd-say", "--version"),
                Map.of(),
                PROBE_TIMEOUT
        );
        if (spdSay.isSuccess()) {
            return new TtsAvailability(true, PROVIDER_ID, "tts.status.ready", "Linux spd-say");
        }
        return new TtsAvailability(
                false, PROVIDER_ID, "tts.status.systemUnavailable", "Neither espeak-ng nor spd-say found");
    }

    private List<TtsVoice> listWindowsVoices(TargetLanguage language) {
        ProcessExecutionResult result = processRunner.run(
                List.of("powershell", "-NoProfile", "-NonInteractive", "-Command", WINDOWS_LIST_VOICES_SCRIPT),
                Map.of(),
                PROBE_TIMEOUT
        );
        if (!result.isSuccess() || result.stdout().isBlank()) {
            return List.of();
        }
        String prefix = language == TargetLanguage.JA ? "ja" : "en";
        List<TtsVoice> voices = new ArrayList<>();
        for (String line : result.stdout().split("\\R")) {
            String[] parts = line.split("\\|", 2);
            if (parts.length == 2 && parts[1].toLowerCase(Locale.ROOT).startsWith(prefix)) {
                String name = parts[0].trim();
                if (!name.isEmpty()) {
                    voices.add(new TtsVoice(name, name + " (" + parts[1].trim() + ")", language));
                }
            }
        }
        return List.copyOf(voices);
    }

    private List<TtsVoice> listMacVoices(TargetLanguage language) {
        ProcessExecutionResult result = processRunner.run(
                List.of("say", "-v", "?"),
                Map.of(),
                PROBE_TIMEOUT
        );
        if (!result.isSuccess() || result.stdout().isBlank()) {
            return List.of();
        }
        String localePrefix = language == TargetLanguage.JA ? "ja_" : "en_";
        List<TtsVoice> voices = new ArrayList<>();
        for (String line : result.stdout().split("\\R")) {
            if (line.contains(localePrefix)) {
                String trimmed = line.trim();
                int firstSpace = trimmed.indexOf(' ');
                if (firstSpace > 0) {
                    String voiceName = trimmed.substring(0, firstSpace).trim();
                    voices.add(new TtsVoice(voiceName, voiceName, language));
                }
            }
        }
        return List.copyOf(voices);
    }

    private List<TtsVoice> listLinuxVoices(TargetLanguage language) {
        String langCode = language == TargetLanguage.JA ? "ja" : "en";
        ProcessExecutionResult result = processRunner.run(
                List.of("espeak-ng", "--voices=" + langCode),
                Map.of(),
                PROBE_TIMEOUT
        );
        if (!result.isSuccess() || result.stdout().isBlank()) {
            return List.of();
        }
        List<TtsVoice> voices = new ArrayList<>();
        String[] lines = result.stdout().split("\\R");
        for (int i = 1; i < lines.length; i++) {
            String[] cols = lines[i].trim().split("\\s+");
            if (cols.length >= 4) {
                String name = cols[3];
                voices.add(new TtsVoice(name, name, language));
            }
        }
        return List.copyOf(voices);
    }

    private TtsPlaybackResult speakWindows(TtsPlaybackRequest request) {
        int sapiRate = (int) Math.max(-10, Math.min(10, Math.round((request.speechRate() - 1.0) * 6.0)));
        Map<String, String> env = new HashMap<>();
        env.put("SENKOJE_TTS_TEXT", request.text());
        env.put("SENKOJE_TTS_VOICE", request.preferredVoice());
        env.put("SENKOJE_TTS_RATE", Integer.toString(sapiRate));
        env.put("SENKOJE_TTS_LANG", request.language() == TargetLanguage.JA ? "ja" : "en");

        ProcessExecutionResult result = processRunner.run(
                List.of("powershell", "-NoProfile", "-NonInteractive", "-Command", WINDOWS_SPEAK_SCRIPT),
                env,
                SPEAK_TIMEOUT
        );
        return toPlaybackResult(result);
    }

    private TtsPlaybackResult speakMac(TtsPlaybackRequest request) {
        int wordsPerMinute = (int) Math.max(90, Math.min(360, Math.round(180.0 * request.speechRate())));
        List<String> command = new ArrayList<>();
        command.add("say");
        command.add("-r");
        command.add(Integer.toString(wordsPerMinute));
        if (!request.preferredVoice().isBlank()) {
            command.add("-v");
            command.add(request.preferredVoice());
        }
        command.add("--");
        command.add(request.text());

        ProcessExecutionResult result = processRunner.run(command, Map.of(), SPEAK_TIMEOUT);
        return toPlaybackResult(result);
    }

    private TtsPlaybackResult speakLinux(TtsPlaybackRequest request) {
        int wordsPerMinute = (int) Math.max(80, Math.min(350, Math.round(170.0 * request.speechRate())));
        String defaultVoice = request.language() == TargetLanguage.JA ? "ja" : "en";
        String voice = request.preferredVoice().isBlank() ? defaultVoice : request.preferredVoice();

        List<String> espeakCmd = List.of(
                "espeak-ng",
                "-v", voice,
                "-s", Integer.toString(wordsPerMinute),
                "--",
                request.text()
        );
        ProcessExecutionResult espeakResult = processRunner.run(espeakCmd, Map.of(), SPEAK_TIMEOUT);
        if (!espeakResult.launchFailed()) {
            return toPlaybackResult(espeakResult);
        }

        // Dự phòng bằng spd-say nếu espeak-ng không có trên máy Linux
        int ratePercent = (int) Math.max(-50, Math.min(100, Math.round((request.speechRate() - 1.0) * 100.0)));
        List<String> spdCmd = new ArrayList<>();
        spdCmd.add("spd-say");
        spdCmd.add("-w");
        spdCmd.add("-l");
        spdCmd.add(defaultVoice);
        spdCmd.add("-r");
        spdCmd.add(Integer.toString(ratePercent));
        if (!request.preferredVoice().isBlank()) {
            spdCmd.add("-y");
            spdCmd.add(request.preferredVoice());
        }
        spdCmd.add("--");
        spdCmd.add(request.text());

        ProcessExecutionResult spdResult = processRunner.run(spdCmd, Map.of(), SPEAK_TIMEOUT);
        return toPlaybackResult(spdResult);
    }

    private TtsPlaybackResult toPlaybackResult(ProcessExecutionResult result) {
        if (result.isSuccess()) {
            return new TtsPlaybackResult(true, PROVIDER_ID, "tts.status.played", "", false);
        }
        if (result.cancelled()) {
            return new TtsPlaybackResult(false, PROVIDER_ID, "tts.status.cancelled", "", true);
        }
        if (result.timedOut()) {
            return new TtsPlaybackResult(false, PROVIDER_ID, "tts.status.timeout", result.stderr(), false);
        }
        if (result.launchFailed()) {
            return new TtsPlaybackResult(false, PROVIDER_ID, "tts.status.systemUnavailable", result.stderr(), false);
        }
        return new TtsPlaybackResult(false, PROVIDER_ID, "tts.status.failed", result.stderr(), false);
    }

    private static OsFamily detectOsFamily(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("win")) {
            return OsFamily.WINDOWS;
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return OsFamily.MACOS;
        }
        if (normalized.contains("nux") || normalized.contains("nix")) {
            return OsFamily.LINUX;
        }
        return OsFamily.OTHER;
    }

    private enum OsFamily {
        WINDOWS,
        MACOS,
        LINUX,
        OTHER
    }
}
