/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.inference.pipelines.nlp;

import org.elasticsearch.xpack.core.ml.inference.deployment.PyTorchResult;
import org.elasticsearch.xpack.core.ml.inference.results.InferenceResults;
import org.elasticsearch.xpack.ml.inference.pipelines.nlp.tokenizers.BertTokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.xpack.ml.inference.pipelines.nlp.NerProcessor.IobTag;

class NerResultProcessor implements NlpPipeline.ResultProcessor {

    private final BertTokenizer.TokenizationResult tokenization;

    NerResultProcessor(BertTokenizer.TokenizationResult tokenization) {
        this.tokenization = Objects.requireNonNull(tokenization);
    }

    @Override
    public InferenceResults processResult(PyTorchResult pyTorchResult) {
        if (tokenization.getTokens().isEmpty()) {
            return new NerResult(Collections.emptyList());
        }
        // TODO It might be best to do the soft max after averaging scores for
        // sub-tokens. If we had a word that is "elastic" which is tokenized to
        // "el" and "astic" then perhaps we get a prediction for org of 10 for "el"
        // and -5 for "astic". Averaging after softmax would produce a prediction
        // of maybe (1 + 0) / 2 = 0.5 while before softmax it'd be exp(10 - 5) / normalization
        // which could easily be close to 1.
        double[][] normalizedScores = NlpHelpers.convertToProbabilitesBySoftMax(pyTorchResult.getInferenceResult());
        List<TaggedToken> taggedTokens = tagTokens(normalizedScores);
        List<EntityGroup> entities = groupTaggedTokens(taggedTokens);
        return new NerResult(entities);
    }

    /**
     * Here we tag each token with the IoB label that has the max score.
     * Additionally, we merge sub-tokens that are part of the same word
     * in the original input replacing them with a single token that
     * gets labelled based on the average score of all its sub-tokens.
     */
    private List<TaggedToken> tagTokens(double[][] scores) {
        List<TaggedToken> taggedTokens = new ArrayList<>();
        int startTokenIndex = 0;
        while (startTokenIndex < tokenization.getTokens().size()) {
            int inputMapping = tokenization.getTokenMap()[startTokenIndex];
            if (inputMapping < 0) {
                // This token does not map to a token in the input (special tokens)
                startTokenIndex++;
                continue;
            }
            int endTokenIndex = startTokenIndex;
            StringBuilder word = new StringBuilder(tokenization.getTokens().get(startTokenIndex));
            while (endTokenIndex < tokenization.getTokens().size() - 1 && tokenization.getTokenMap()[endTokenIndex + 1] == inputMapping) {
                endTokenIndex++;
                // TODO Here we try to get rid of the continuation hashes at the beginning of sub-tokens.
                // It is probably more correct to implement detokenization on the tokenizer
                // that does reverse lookup based on token IDs.
                String endTokenWord = tokenization.getTokens().get(endTokenIndex).substring(2);
                word.append(endTokenWord);
            }
            double[] avgScores = Arrays.copyOf(scores[startTokenIndex], IobTag.values().length);
            for (int i = startTokenIndex + 1; i <= endTokenIndex; i++) {
                for (int j = 0; j < scores[i].length; j++) {
                    avgScores[j] += scores[i][j];
                }
            }
            for (int i = 0; i < avgScores.length; i++) {
                avgScores[i] /= endTokenIndex - startTokenIndex + 1;
            }
            int maxScoreIndex = NlpHelpers.argmax(avgScores);
            double score = avgScores[maxScoreIndex];
            taggedTokens.add(new TaggedToken(word.toString(), IobTag.values()[maxScoreIndex], score));
            startTokenIndex = endTokenIndex + 1;
        }
        return taggedTokens;
    }

    /**
     * Now that we have merged sub-tokens and tagged them with their IoB label,
     * we group tokens together into the final entity groups. Effectively,
     * we want to group B_X I_X B_X so that it results into two
     * entities, one for the first B_X I_X and another for the latter B_X,
     * where X is the same entity.
     * When multiple tokens are grouped together, the entity score is the
     * mean score of the tokens.
     */
    private List<EntityGroup> groupTaggedTokens(List<TaggedToken> tokens) {
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }
        List<EntityGroup> entities = new ArrayList<>();
        int startTokenIndex = 0;
        while (startTokenIndex < tokens.size()) {
            TaggedToken token = tokens.get(startTokenIndex);
            if (token.tag.getEntity() == NerProcessor.Entity.NONE) {
                startTokenIndex++;
                continue;
            }
            StringBuilder entityWord = new StringBuilder(token.word);
            int endTokenIndex = startTokenIndex + 1;
            double scoreSum = token.score;
            while (endTokenIndex < tokens.size()) {
                TaggedToken endToken = tokens.get(endTokenIndex);
                if (endToken.tag.isBeginning() || endToken.tag.getEntity() != token.tag.getEntity()) {
                    break;
                }
                // TODO Here we add a space between tokens.
                // It is probably more correct to implement detokenization on the tokenizer
                // that does reverse lookup based on token IDs.
                entityWord.append(" ").append(endToken.word);
                scoreSum += endToken.score;
                endTokenIndex++;
            }
            entities.add(new EntityGroup(token.tag.getEntity(), scoreSum / (endTokenIndex - startTokenIndex), entityWord.toString()));
            startTokenIndex = endTokenIndex;
        }

        return entities;
    }

    private static class TaggedToken {
        private final String word;
        private final IobTag tag;
        private final double score;

        private TaggedToken(String word, IobTag tag, double score) {
            this.word = word;
            this.tag = tag;
            this.score = score;
        }
    }
}
