# Routing eval

Kev server: `jaredpalmer/kev-0.8b@v1.0` on MLX.

Model: `kev-latest`, 28 requests, median kev latency 49 ms.

Each route activates when its yes/no probability is greater than or equal to the threshold. When none activates, the application routes to `general` as its fallback.

## Threshold accuracy

| Threshold | single | indirect | multi | general | total | fallbacks |
|---:|---:|---:|---:|---:|---:|---:|
| 0.3 | 1/8 | 1/7 | 4/8 | 0/5 | 6/28 | 0 |
| 0.4 | 2/8 | 1/7 | 5/8 | 1/5 | 9/28 | 1 |
| 0.5 | 4/8 | 1/7 | 3/8 | 1/5 | 9/28 | 2 |
| 0.6 | 5/8 | 3/7 | 3/8 | 3/5 | 14/28 | 7 |
| 0.7 | 7/8 | 4/7 | 3/8 | 4/5 | 18/28 | 11 |

## Per request (threshold ≥ 0.5)

| Request | Expected | Reservation | Weather | Cost | General | Routed | OK |
|---|---|---:|---:|---:|---:|---|---|
| Will it rain in Lisbon next Tuesday? | weather | 0.57 | 0.87 | 0.14 | 0.31 | reservation+weather | **no** |
| What's the forecast for Edinburgh this weekend? | weather | 0.26 | 0.91 | 0.08 | 0.29 | weather | yes |
| How much does it cost to rent an SUV for 5 days? | cost | 0.71 | 0.18 | 0.78 | 0.22 | reservation+cost | **no** |
| What's the fee for returning the car at a different location? | cost | 0.69 | 0.20 | 0.72 | 0.29 | reservation+cost | **no** |
| Please reserve a car for next week | reservation | 0.87 | 0.30 | 0.64 | 0.20 | reservation+cost | **no** |
| I need to cancel my booking for Friday. | reservation | 0.93 | 0.33 | 0.47 | 0.17 | reservation | yes |
| Hi there! | general | 0.45 | 0.24 | 0.31 | 0.68 | general | yes |
| Thanks, that's all I needed. | general | 0.33 | 0.12 | 0.11 | 0.35 | general (fallback) | yes |
| Will I need snow chains to drive to Chamonix in January? | weather | 0.58 | 0.71 | 0.34 | 0.26 | reservation+weather | **no** |
| Is the young-driver surcharge included in the quote you sent me? | cost | 0.55 | 0.22 | 0.37 | 0.26 | reservation | **no** |
| Can I push my pick-up time from 9 to 11? | reservation | 0.80 | 0.55 | 0.68 | 0.50 | reservation+weather+cost+general | **no** |
| Do you have electric cars at the Porto airport branch? | reservation | 0.60 | 0.33 | 0.54 | 0.30 | reservation+cost | **no** |
| Is a weekly rental cheaper than paying day by day? | cost | 0.82 | 0.47 | 0.78 | 0.46 | reservation+cost | **no** |
| Can I bring my dog in the rental car? | general | 0.83 | 0.72 | 0.68 | 0.55 | reservation+weather+cost+general | **no** |
| What documents do I need to bring to pick up the car? | reservation | 0.47 | 0.07 | 0.25 | 0.14 | general (fallback) | yes |
| Is it worth upgrading to a convertible for a weekend in Nice if it might rain? | weather+cost | 0.63 | 0.67 | 0.45 | 0.26 | reservation+weather | **no** |
| How much extra is a convertible, and will the weather in Nice be good enough next weekend? | weather+cost | 0.61 | 0.64 | 0.40 | 0.20 | reservation+weather | **no** |
| Book me a car for Saturday and tell me what it'll cost with full insurance. | reservation+cost | 0.83 | 0.34 | 0.87 | 0.10 | reservation+cost | yes |
| Should I pick up the car Friday or Saturday given the storm forecast? | weather+reservation | 0.76 | 0.78 | 0.28 | 0.23 | reservation+weather | yes |
| I want to extend my rental by two days; how much more is that? | reservation+cost | 0.81 | 0.33 | 0.48 | 0.28 | reservation | **no** |
| What's the cheapest car that can handle snowy mountain roads in February? | cost+weather | 0.41 | 0.29 | 0.74 | 0.28 | cost | **no** |
| Can I change my booking to a 4x4, and will I need one for the weather in the Highlands? | reservation+weather | 0.87 | 0.76 | 0.75 | 0.24 | reservation+weather+cost | yes |
| Give me the total for a week in Seville in August, and will it be too hot to drive without air conditioning? | cost+weather | 0.64 | 0.74 | 0.50 | 0.23 | reservation+weather | **no** |
| What's your company's phone number? | general | 0.43 | 0.34 | 0.56 | 0.34 | cost | **no** |
| Can you recommend a good restaurant in Lisbon? | general | 0.69 | 0.08 | 0.39 | 0.23 | reservation | **no** |
| Do you offer jobs for drivers? | general | 0.63 | 0.64 | 0.89 | 0.53 | reservation+weather+cost+general | **no** |
| What are your opening hours at Lisbon airport? | general | 0.60 | 0.11 | 0.18 | 0.34 | reservation | yes |
| How long does it take to drive from Lisbon to Porto? | general | 0.53 | 0.16 | 0.14 | 0.31 | reservation | **no** |

General fallbacks at threshold 0.5: 2 of 28 requests.
