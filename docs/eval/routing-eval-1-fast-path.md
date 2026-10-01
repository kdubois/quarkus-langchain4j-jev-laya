# Routing eval

Model: `jev-1.13.0`, 28 requests, median Jev latency 266 ms.

## Policies

| Policy | single | indirect | multi | general | total |
|---|---|---|---|---|---|
| Choice only | 8/8 | 7/7 | 0/8 | 5/5 | 20/28 |
| confidence ≥ 0.6, Noul > 0.3 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| confidence ≥ 0.6, Noul > 0.4 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| confidence ≥ 0.6, Noul > 0.5 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| confidence ≥ 0.6, Noul > 0.6 | 8/8 | 7/7 | 3/8 | 5/5 | 23/28 |
| confidence ≥ 0.7, Noul > 0.3 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| confidence ≥ 0.7, Noul > 0.4 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| confidence ≥ 0.7, Noul > 0.5 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| confidence ≥ 0.7, Noul > 0.6 | 8/8 | 7/7 | 3/8 | 5/5 | 23/28 |
| confidence ≥ 0.8, Noul > 0.3 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| confidence ≥ 0.8, Noul > 0.4 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| confidence ≥ 0.8, Noul > 0.5 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| confidence ≥ 0.8, Noul > 0.6 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| confidence ≥ 0.9, Noul > 0.3 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| confidence ≥ 0.9, Noul > 0.4 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| confidence ≥ 0.9, Noul > 0.5 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| confidence ≥ 0.9, Noul > 0.6 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| confidence ≥ never, Noul > 0.3 | 6/8 | 5/7 | 8/8 | 5/5 | 24/28 |
| confidence ≥ never, Noul > 0.4 | 7/8 | 5/7 | 8/8 | 5/5 | 25/28 |
| confidence ≥ never, Noul > 0.5 | 7/8 | 6/7 | 8/8 | 5/5 | 26/28 |
| confidence ≥ never, Noul > 0.6 | 7/8 | 6/7 | 7/8 | 5/5 | 25/28 |
| margin ≥ 0.6, Noul > 0.3 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| margin ≥ 0.6, Noul > 0.4 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| margin ≥ 0.6, Noul > 0.5 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| margin ≥ 0.6, Noul > 0.6 | 8/8 | 7/7 | 3/8 | 5/5 | 23/28 |
| margin ≥ 0.7, Noul > 0.3 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| margin ≥ 0.7, Noul > 0.4 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| margin ≥ 0.7, Noul > 0.5 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| margin ≥ 0.7, Noul > 0.6 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| margin ≥ 0.8, Noul > 0.3 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| margin ≥ 0.8, Noul > 0.4 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| margin ≥ 0.8, Noul > 0.5 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| margin ≥ 0.8, Noul > 0.6 | 8/8 | 7/7 | 4/8 | 5/5 | 24/28 |
| margin ≥ 0.9, Noul > 0.3 | 8/8 | 7/7 | 6/8 | 5/5 | 26/28 |
| margin ≥ 0.9, Noul > 0.4 | 8/8 | 7/7 | 6/8 | 5/5 | 26/28 |
| margin ≥ 0.9, Noul > 0.5 | 8/8 | 7/7 | 6/8 | 5/5 | 26/28 |
| margin ≥ 0.9, Noul > 0.6 | 8/8 | 7/7 | 5/8 | 5/5 | 25/28 |
| margin ≥ never, Noul > 0.3 | 6/8 | 5/7 | 8/8 | 5/5 | 24/28 |
| margin ≥ never, Noul > 0.4 | 7/8 | 5/7 | 8/8 | 5/5 | 25/28 |
| margin ≥ never, Noul > 0.5 | 7/8 | 6/7 | 8/8 | 5/5 | 26/28 |
| margin ≥ never, Noul > 0.6 | 7/8 | 6/7 | 7/8 | 5/5 | 25/28 |

## Per request (default policy: confidence ≥ 0.8, Noul > 0.5)

| Request | Expected | Choice (conf / margin) | Noul res / wea / cost | Routed | OK |
|---|---|---|---|---|---|
| Will it rain in Lisbon next Tuesday? | weather | weather (1.00 / 1.00) | 0.02 / 0.97 / 0.02 | weather | yes |
| What's the forecast for Edinburgh this weekend? | weather | weather (1.00 / 1.00) | 0.02 / 0.96 / 0.02 | weather | yes |
| How much does it cost to rent an SUV for 5 days? | cost | cost (0.98 / 0.96) | 0.40 / 0.06 / 0.95 | cost | yes |
| What's the fee for returning the car at a different location? | cost | cost (0.98 / 0.98) | 0.66 / 0.02 / 0.95 | cost | yes |
| Please reserve a car for next week | reservation | reservation (1.00 / 1.00) | 0.79 / 0.06 / 0.22 | reservation | yes |
| I need to cancel my booking for Friday. | reservation | reservation (1.00 / 1.00) | 0.87 / 0.02 / 0.13 | reservation | yes |
| Hi there! | general | general (1.00 / 1.00) | 0.16 / 0.08 / 0.07 | general | yes |
| Thanks, that's all I needed. | general | general (1.00 / 1.00) | 0.14 / 0.09 / 0.09 | general | yes |
| Will I need snow chains to drive to Chamonix in January? | weather | weather (0.89 / 0.86) | 0.10 / 0.78 / 0.04 | weather | yes |
| Is the young-driver surcharge included in the quote you sent me? | cost | cost (0.98 / 0.98) | 0.84 / 0.02 / 0.91 | cost | yes |
| Can I push my pick-up time from 9 to 11? | reservation | reservation (1.00 / 1.00) | 0.90 / 0.06 / 0.18 | reservation | yes |
| Do you have electric cars at the Porto airport branch? | reservation | general (0.37 / 0.06) | 0.17 / 0.02 / 0.05 | general | yes |
| Is a weekly rental cheaper than paying day by day? | cost | cost (1.00 / 1.00) | 0.48 / 0.03 / 0.95 | cost | yes |
| Can I bring my dog in the rental car? | general | reservation (0.60 / 0.42) | 0.46 / 0.04 / 0.19 | general | yes |
| What documents do I need to bring to pick up the car? | reservation | reservation (1.00 / 1.00) | 0.65 / 0.01 / 0.03 | reservation | yes |
| Is it worth upgrading to a convertible for a weekend in Nice if it might rain? | weather+cost | weather (0.46 / 0.35) | 0.48 / 0.81 / 0.51 | weather+cost | yes |
| How much extra is a convertible, and will the weather in Nice be good enough next weekend? | weather+cost | general (0.31 / 0.10) | 0.68 / 0.90 / 0.85 | weather+cost+reservation | yes |
| Book me a car for Saturday and tell me what it'll cost with full insurance. | reservation+cost | reservation (0.97 / 0.96) | 0.84 / 0.05 / 0.95 | reservation | **no** |
| Should I pick up the car Friday or Saturday given the storm forecast? | weather+reservation | reservation (0.90 / 0.86) | 0.65 / 0.84 / 0.07 | reservation | **no** |
| I want to extend my rental by two days; how much more is that? | reservation+cost | reservation (0.76 / 0.64) | 0.91 / 0.03 / 0.93 | cost+reservation | yes |
| What's the cheapest car that can handle snowy mountain roads in February? | cost+weather | cost (0.45 / 0.22) | 0.09 / 0.69 / 0.92 | cost+weather | yes |
| Can I change my booking to a 4x4, and will I need one for the weather in the Highlands? | reservation+weather | reservation (0.99 / 1.00) | 0.92 / 0.76 / 0.31 | reservation | **no** |
| Give me the total for a week in Seville in August, and will it be too hot to drive without air conditioning? | cost+weather | cost (0.46 / 0.32) | 0.28 / 0.95 / 0.75 | weather+cost | yes |
| What's your company's phone number? | general | general (1.00 / 1.00) | 0.06 / 0.01 / 0.03 | general | yes |
| Can you recommend a good restaurant in Lisbon? | general | general (0.99 / 0.98) | 0.02 / 0.03 / 0.12 | general | yes |
| Do you offer jobs for drivers? | general | general (1.00 / 1.00) | 0.06 / 0.03 / 0.06 | general | yes |
| What are your opening hours at Lisbon airport? | general | general (0.77 / 0.66) | 0.16 / 0.02 / 0.03 | general | yes |
| How long does it take to drive from Lisbon to Porto? | general | general (1.00 / 1.00) | 0.03 / 0.06 / 0.03 | general | yes |
