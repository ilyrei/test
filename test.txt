# LLM Number Selection Bias Analysis Report

## Introduction

When interacting with large language models (LLMs), it is common to assume that they produce "random" outputs. For instance, when using a prompt like:

```
Pick a number between 1 and 100, and just respond with the number.
```

with `temperature=1.0`, it seems that the model would select numbers uniformly at random between 1 and 100.

However, my experiments revealed that certain numbers are repeatedly chosen more often than others. In this report, we share these experimental results and examine the reasons behind this phenomenon, as well as mathematical methods to analyze it.

## Experimental Setup

* Models: GPT-3.5-turbo, GPT-4o, GPT-4o-mini
* Prompt: "Pick a number between 1 and 100, and just respond with the number."
* Sampling parameters: `temperature = 1.0`
* Repetitions: 1,000 times per model

For each API call, I recorded the number returned by the model and excluded any non-numeric responses. We then aggregated the frequency of each number and compared it against the expected uniform distribution (expected frequency for each number = 10).

## Observed Results

All three models exhibited noticeable bias toward certain numbers. Visualizing the results with a bar chart clearly shows that the number distribution is far from uniform, with some numbers appearing much more frequently than others.

## Possible Causes of Bias

Even though LLM outputs appear “random,” several factors contribute to the observed bias:

1. **Tokenizer Effects**
   Models tokenize numbers as text. For example, `7` may be represented as a single token, while `42` might require two tokens. Probability distributions at the token level do not necessarily translate to uniform probabilities at the number level, which can make certain numbers more likely to be chosen.

2. **Training Data Bias**
   LLMs are trained on large corpora of web text, documents, and code. Numbers that appear more frequently in the training data may have higher selection probabilities.

3. **Decoding Methods**
   Sampling methods such as temperature, top-p, and top-k do not guarantee uniform selection at the number level. Even with `temperature=1.0`, token-level sampling probabilities may remain uneven.

4. **Prompt Design**
   Natural language prompts like “Pick a number” can encourage the model to follow patterns that were common in the training data, leading to preference for certain numbers.

## Mathematical Analysis of LLM Number Selection

I quantified the bias in the observed number distributions using three mathematical metrics:

### 1. Chi-Squared Goodness-of-Fit Test

* **Purpose:** Test whether the observed number distribution differs significantly from a uniform distribution.
* **Formula:**
  [
  \chi^2 = \sum \frac{(f_i - E_i)^2}{E_i}
  ]
  where (f_i) is the observed frequency of each number, and (E_i = N/100 = 10) is the expected frequency.
* Interpretation: A large (\chi^2) value and a p-value near 0 indicate that the observed distribution significantly deviates from uniformity.

### 2. Entropy (H)

* Purpose: Quantify the randomness of number selection.
* Formula:
  [
  H = - \sum p_i \log_2 p_i
  ]
  where (p_i = f_i / N) is the observed probability of each number.
* Interpretation: For a uniform distribution, (H \approx 6.644 \text{ bits}). Lower entropy indicates concentration on a few numbers.

### 3. KL-Divergence (D_KL)

* Purpose: Measure the deviation of the observed distribution from uniform.
* Formula:
  [
  D_{KL}(P || Q) = \sum p_i \log_2 \frac{p_i}{q_i}
  ]
  where (P) is the observed distribution and (Q) is the uniform distribution ((q_i = 0.01)).
* Interpretation: Higher values indicate greater deviation from uniformity.

## Experimental Results (GPT-4o Example)

* **Chi-Squared Test:** (\chi^2 = 55307.2), p-value ≈ 0.0

* **Entropy:** H = 2.35 bits

* **KL-Divergence:** D_KL(P || Q) = 4.29 bits

* Interpretation:

  * Chi-squared test indicates statistically significant bias.
  * Low entropy shows selection is concentrated on a few numbers.
  * High KL-divergence indicates the observed distribution deviates strongly from uniform.

## Overall Interpretation

* The model does not select numbers uniformly.
* Certain numbers are repeatedly chosen, while others are rarely selected.
* Even with `temperature=1.0`, tokenizer structure, training data patterns, and decoding methods introduce strong bias.
* LLMs are therefore **unsuitable as random number generators or uniform sampling tools**.

## Practical Implications

* Avoid using LLMs for random number generation or uniform sampling.
* For experiments or tests requiring truly random numbers, use external RNGs.
* To reduce model bias:

  * Design prompts as clearly as possible.
  * Enforce structured outputs (e.g., JSON).
  * Post-process model outputs to achieve a uniform distribution if necessary.

## Conclusion

This experiment confirms that LLMs, despite appearing to produce random outputs, exhibit significant internal bias that can lead to repeated selection of specific outputs. Understanding and controlling this bias is crucial not only for number selection but also for tasks like text summarization and sentence generation.
