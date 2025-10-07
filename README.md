# LLM Number Selection Bias Analysis Report

## Introduction

When interacting with LLMs, it is common to assume that they produce "random" outputs. For example, when using a prompt like:

```
Pick a number between 1 and 100, and just respond with the number.
```

with `temperature=1.0`, it seems that the model would select numbers randomly between 1 and 100.

At first, I thought ‘This should be completely random.’ But after running 1,000 trials, I was surprised to see certain numbers appearing repeatedly.
However, my experiments revealed that certain numbers are repeatedly chosen more often than others. In this report, we share these experimental results and examine the reasons behind this phenomenon.

## Experimental Setup

* Models: GPT-3.5-turbo, GPT-4o, GPT-4o-mini
* Prompt: "Pick a number between 1 and 100, and just respond with the number."
* Sampling parameters: `temperature = 1.0`
* Repetitions: 1,000 times per model

For each API call, I recorded the number returned by the model. 

## Observed Results

All three models exhibited noticeable bias toward certain numbers. Visualizing the results with a bar chart clearly shows that the number distribution is far from uniform, with some numbers appearing much more frequently than others.

## Possible Causes of Bias

Even though LLM outputs appear “random,” several factors contribute to the observed bias:

1. **Training Data Bias**
   LLMs are trained on large corpora of web text, documents, and code. Numbers that appear more frequently in the training data may have higher selection probabilities.

2. **Decoding Methods**
   Sampling methods such as temperature, top-p, and top-k do not guarantee uniform selection at the number level. Even with `temperature=1.0`, token-level sampling probabilities may remain uneven.

## Mathematical Analysis of LLM Number Selection

I quantified the bias in the observed number distributions using three mathematical metrics:

### 1. Chi-Squared Goodness-of-Fit Test

* Purpose: Test whether the observed number distribution differs significantly from a uniform distribution.
* Formula:
  
  ![chi-squared](https://latex.codecogs.com/png.latex?%5Cchi%5E2%20%3D%20%5Csum%20%5Cfrac%7B(f_i-E_i)%5E2%7D%7BE_i%7D)
  
  where (f_i) is the observed frequency of each number, and (E_i = N/100 = 10) is the expected frequency.
* Interpretation: A large (\chi^2) value and a p-value near 0 indicate that the observed distribution significantly deviates from uniformity.

### 2. Entropy (H)

* Purpose: Quantify the randomness of number selection.
* Formula:

  ![entropy](https://latex.codecogs.com/png.latex?H%20%3D%20-%20%5Csum%20p_i%20%5Clog_2%20p_i)

  where (p_i = f_i / N) is the observed probability of each number.
* Interpretation: For a uniform distribution, (H \approx 6.644 \text{ bits}). Lower entropy indicates concentration on a few numbers.

### 3. KL-Divergence (D_KL)

* Purpose: Measure the deviation of the observed distribution from uniform.
* Formula:

  ![kl-divergence](https://latex.codecogs.com/png.latex?D_%7BKL%7D(P%20%7C%7C%20Q)%20%3D%20%5Csum%20p_i%20%5Clog_2%20%5Cfrac%7Bp_i%7D%7Bq_i%7D)

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
In my future experiments, I plan to avoid using LLM numbers directly and will rely on external RNGs. I recommend you do the same if you want truly unbiased results.
