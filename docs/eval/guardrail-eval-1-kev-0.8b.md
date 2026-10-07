# Kev 0.8B guardrail eval

Model: `kev-latest`. Dataset: 24 labeled customer-request and drafted-reply pairs. Median Kev latency: 34 ms.

This uses the same prompts and thresholds as the application. A probability below 0.4 retries the reply, a probability above 0.6 passes it, and a probability from 0.4 through 0.6 passes it while marking it for review. For accuracy, both pass outcomes count as `pass`.

## Results

| Guardrail | Correct | False accepts | False rejects | Review band |
|---|---:|---:|---:|---:|
| Relevance | 11/12 | 1 | 0 | 2 |
| Completeness | 9/12 | 2 | 1 | 3 |
| **Total** | **20/24** | **3** | **1** | **5** |

## Per case

| ID | Guardrail | Expected | Probability | Action | Correct |
|---|---|---|---:|---|---|
| `relevance-weather-answer` | relevance | pass | 0.888 | pass | yes |
| `relevance-greeting` | relevance | pass | 0.677 | pass | yes |
| `relevance-weather-partial-fanout` | relevance | pass | 0.640 | pass | yes |
| `relevance-cost-partial-fanout` | relevance | pass | 0.456 | pass-review | yes |
| `relevance-clarifying-question` | relevance | pass | 0.473 | pass-review | yes |
| `relevance-opening-hours` | relevance | pass | 0.757 | pass | yes |
| `relevance-weather-off-topic-price` | relevance | retry | 0.268 | retry | yes |
| `relevance-cancellation-off-topic` | relevance | retry | 0.392 | retry | yes |
| `relevance-evasive` | relevance | retry | 0.136 | retry | yes |
| `relevance-generic-acknowledgement` | relevance | retry | 0.756 | pass | **no** |
| `relevance-cost-off-topic-weather` | relevance | retry | 0.323 | retry | yes |
| `relevance-booking-deflection` | relevance | retry | 0.341 | retry | yes |
| `completeness-weather-and-cost` | completeness | pass | 0.390 | retry | **no** |
| `completeness-booking-and-cost` | completeness | pass | 0.543 | pass-review | yes |
| `completeness-weather-and-pickup` | completeness | pass | 0.717 | pass | yes |
| `completeness-single-weather` | completeness | pass | 0.934 | pass | yes |
| `completeness-greeting` | completeness | pass | 0.441 | pass-review | yes |
| `completeness-direct-but-unverified` | completeness | pass | 0.571 | pass-review | yes |
| `completeness-missing-cost` | completeness | retry | 0.377 | retry | yes |
| `completeness-missing-weather` | completeness | retry | 0.336 | retry | yes |
| `completeness-missing-cost-after-booking` | completeness | retry | 0.684 | pass | **no** |
| `completeness-off-topic` | completeness | retry | 0.160 | retry | yes |
| `completeness-evasive` | completeness | retry | 0.255 | retry | yes |
| `completeness-missing-pickup-answer` | completeness | retry | 0.651 | pass | **no** |

## Scope

These guardrails judge whether a reply addresses the request. They do not check whether an answer is factually correct. The `completeness-direct-but-unverified` case makes that boundary explicit: a direct but invented opening-hours answer should pass this guardrail and would need a separate factuality check.
