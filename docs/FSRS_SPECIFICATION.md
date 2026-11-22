# FSRS-6 Scheduling Engine Specification - SenkoJE

## 1. Algorithm Selection

FSRS-6 is the only supported scheduling algorithm in SenkoJE.

Do not implement SM-2 as an alternative.

Use a verified FSRS-6 implementation or a carefully validated Java port of the reference algorithm.

The implementation must preserve algorithm compatibility, parameter definitions, rating mappings, scheduling behavior, and memory state transitions.

Do not invent scheduling formulas or modify algorithm parameters without a documented reason.

## 2. Core Concepts

FSRS uses three core memory concepts:

* Stability: how long a memory remains relatively stable.
* Difficulty: estimated difficulty of remembering an item.
* Retrievability: estimated probability of recalling an item at a given time.

Card ratings:

| Rating | Value | Meaning |
| ----- | ----- | ----- |
| Again | 1 | Failed recall |
| Hard | 2 | Difficult recall |
| Good | 3 | Successful recall |
| Easy | 4 | Very easy recall |

## 3. Review States

Supported states:

* `NEW`
* `LEARNING`
* `REVIEW`
* `RELEARNING`

The FSRS scheduler must determine valid transitions.

Do not manually change card states in JavaFX controllers.

## 4. Scheduling Configuration

Provide configurable settings:

| Setting | Purpose |
| ----- | ----- |
| Desired retention | Target recall probability |
| Maximum interval | Upper scheduling limit |
| Learning steps | Short-term learning intervals |
| Relearning steps | Recovery intervals after forgetting |
| New cards per day | Daily introduction limit |
| Maximum reviews per day | Daily review limit |
| Enable fuzzing | Spread due dates where supported |

Initial desired retention may default to `0.90`, but must remain configurable.

The application must document that desired retention affects workload and scheduling intervals.

## 5. FSRS Parameters

* Store algorithm parameters with an explicit version.
* Store the FSRS algorithm version used for each scheduling operation.
* Provide safe default parameters.
* Do not silently replace parameters during upgrades.
* Support future parameter optimization only after sufficient review history exists.
* Parameter optimization must never overwrite user data without confirmation.

## 6. Scheduling Requirements

The scheduler must:

1. Receive the current review state.
2. Receive the user's rating.
3. Determine elapsed time.
4. Calculate the next memory state.
5. Calculate the next review interval.
6. Apply configured scheduling limits.
7. Return the new review state.
8. Persist the result transactionally.

All four rating outcomes must have automated tests.

Use deterministic test inputs and reference test vectors to validate the Java implementation.

The scheduler must not depend on the system clock directly. Inject a clock or pass explicit timestamps to make tests reproducible.
