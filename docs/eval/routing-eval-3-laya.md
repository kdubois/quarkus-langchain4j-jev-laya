# Routing eval

Model: `laya-rl-agent`, 28 requests, median laya latency 76 ms.

## Policies

| Policy | single | indirect | multi | general | total |
|---|---|---|---|---|---|
| Choice only | 8/8 | 7/7 | 0/8 | 3/5 | 18/28 |
| Choice fast path (confidence ≥ 0.7), then Noul > 0.5 | 8/8 | 7/7 | 1/8 | 3/5 | 19/28 |
| Choice fast path (confidence ≥ 0.8), then Noul > 0.5 | 8/8 | 7/7 | 1/8 | 3/5 | 19/28 |
| Choice fast path (confidence ≥ 0.9), then Noul > 0.5 | 8/8 | 7/7 | 1/8 | 3/5 | 19/28 |
| Nouls first (Noul > 0.3), then Choice | 7/8 | 5/7 | 4/8 | 3/5 | 19/28 |
| Nouls first (Noul > 0.4), then Choice | 8/8 | 7/7 | 2/8 | 3/5 | 20/28 |
| Nouls first (Noul > 0.5), then Choice | 8/8 | 7/7 | 1/8 | 3/5 | 19/28 |
| Nouls first (Noul > 0.6), then Choice | 8/8 | 7/7 | 0/8 | 3/5 | 18/28 |
| Nouls first (Noul > 0.7), then Choice | 8/8 | 7/7 | 0/8 | 3/5 | 18/28 |

## Per request (Nouls first, Noul > 0.5)

| Request | Expected | Choice (confidence) | Noul res / wea / cost | Routed | OK |
|---|---|---|---|---|---|
| Will it rain in Lisbon next Tuesday? | weather | weather (0.60) | 0.08 / 0.64 / 0.06 | weather | yes |
| What's the forecast for Edinburgh this weekend? | weather | weather (0.49) | 0.11 / 0.62 / 0.05 | weather | yes |
| How much does it cost to rent an SUV for 5 days? | cost | cost (0.47) | 0.19 / 0.15 / 0.48 | cost | yes |
| What's the fee for returning the car at a different location? | cost | cost (0.41) | 0.16 / 0.08 / 0.53 | cost | yes |
| Please reserve a car for next week | reservation | reservation (0.33) | 0.60 / 0.12 / 0.25 | reservation | yes |
| I need to cancel my booking for Friday. | reservation | reservation (0.44) | 0.67 / 0.16 / 0.15 | reservation | yes |
| Hi there! | general | general (0.16) | 0.36 / 0.15 / 0.18 | general | yes |
| Thanks, that's all I needed. | general | general (0.15) | 0.14 / 0.12 / 0.13 | general | yes |
| Will I need snow chains to drive to Chamonix in January? | weather | weather (0.25) | 0.19 / 0.51 / 0.21 | weather | yes |
| Is the young-driver surcharge included in the quote you sent me? | cost | cost (0.16) | 0.33 / 0.10 / 0.36 | cost | yes |
| Can I push my pick-up time from 9 to 11? | reservation | reservation (0.11) | 0.36 / 0.15 / 0.17 | reservation | yes |
| Do you have electric cars at the Porto airport branch? | reservation | reservation (0.09) | 0.24 / 0.08 / 0.26 | reservation | yes |
| Is a weekly rental cheaper than paying day by day? | cost | cost (0.10) | 0.32 / 0.15 / 0.46 | cost | yes |
| Can I bring my dog in the rental car? | general | general (0.14) | 0.19 / 0.12 / 0.15 | general | yes |
| What documents do I need to bring to pick up the car? | reservation | reservation (0.09) | 0.38 / 0.07 / 0.21 | reservation | yes |
| Is it worth upgrading to a convertible for a weekend in Nice if it might rain? | weather+cost | weather (0.16) | 0.42 / 0.59 / 0.25 | weather | **no** |
| How much extra is a convertible, and will the weather in Nice be good enough next weekend? | weather+cost | weather (0.16) | 0.22 / 0.52 / 0.19 | weather | **no** |
| Book me a car for Saturday and tell me what it'll cost with full insurance. | reservation+cost | cost (0.15) | 0.37 / 0.17 / 0.36 | cost | **no** |
| Should I pick up the car Friday or Saturday given the storm forecast? | weather+reservation | weather (0.40) | 0.27 / 0.65 / 0.11 | weather | **no** |
| I want to extend my rental by two days; how much more is that? | reservation+cost | reservation (0.10) | 0.41 / 0.17 / 0.47 | reservation | **no** |
| What's the cheapest car that can handle snowy mountain roads in February? | cost+weather | weather (0.03) | 0.22 / 0.25 / 0.46 | weather | **no** |
| Can I change my booking to a 4x4, and will I need one for the weather in the Highlands? | reservation+weather | weather (0.29) | 0.61 / 0.55 / 0.17 | reservation+weather | yes |
| Give me the total for a week in Seville in August, and will it be too hot to drive without air conditioning? | cost+weather | weather (0.21) | 0.24 / 0.44 / 0.34 | weather | **no** |
| What's your company's phone number? | general | reservation (0.10) | 0.17 / 0.14 / 0.14 | reservation | **no** |
| Can you recommend a good restaurant in Lisbon? | general | general (0.11) | 0.19 / 0.12 / 0.19 | general | yes |
| Do you offer jobs for drivers? | general | reservation (0.12) | 0.38 / 0.12 / 0.21 | reservation | **no** |
| What are your opening hours at Lisbon airport? | general | general (0.17) | 0.10 / 0.10 / 0.10 | general | yes |
| How long does it take to drive from Lisbon to Porto? | general | general (0.06) | 0.15 / 0.13 / 0.11 | general | yes |

Fan-outs: 1 of 28 requests.
