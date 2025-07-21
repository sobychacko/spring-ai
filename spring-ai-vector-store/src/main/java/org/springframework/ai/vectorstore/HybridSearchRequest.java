/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore;

import java.util.Objects;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Hybrid search request that combines vector similarity search with keyword/text search.
 * Extends the standard {@link SearchRequest} with additional parameters for hybrid search.
 *
 * <p>Use the {@link HybridSearchRequest#builder()} to create an instance of a {@link HybridSearchRequest}.
 *
 * @author Soby Chacko
 * @since 1.0.0
 */
public class HybridSearchRequest extends SearchRequest {

	/**
	 * Fusion types for combining vector and keyword search results.
	 */
	public enum FusionType {
		/**
		 * Combines results based on relative scores from both search methods.
		 */
		RELATIVE_SCORE,

		/**
		 * Combines results based on their rank in each search method.
		 */
		RANKED,

		/**
		 * Reciprocal Rank Fusion - combines results using a formula that considers
		 * the reciprocal of each document's rank.
		 */
		RRF,

		/**
		 * Combines results using weighted scores from both search methods.
		 */
		WEIGHTED
	}

	/**
	 * Default alpha value (weight) for balancing vector and keyword search.
	 * Value of 0.5 gives equal weight to both search methods.
	 */
	public static final double DEFAULT_ALPHA = 0.5;

	/**
	 * Default fusion type for combining search results.
	 */
	public static final FusionType DEFAULT_FUSION_TYPE = FusionType.RELATIVE_SCORE;

	/**
	 * Alpha parameter controls the balance between vector and keyword search.
	 * Value between 0.0 and 1.0 where:
	 * - 0.0 means only keyword search
	 * - 1.0 means only vector search
	 * - 0.5 means equal weight to both
	 */
	private double alpha = DEFAULT_ALPHA;

	/**
	 * The fusion type to use when combining vector and keyword search results.
	 */
	private FusionType fusionType = DEFAULT_FUSION_TYPE;

	/**
	 * Additional options for hybrid search, implementation specific.
	 */
	@Nullable
	private HybridSearchOptions options;

	/**
	 * Private default constructor to enforce creation via Builder.
	 */
	private HybridSearchRequest() {
		super();
	}

	/**
	 * Private constructor used by the Builder to construct the immutable instance.
	 * It populates all fields directly from the builder's state.
	 * @param parentRequest The fully built SearchRequest instance from the super builder.
	 * @param builder The builder instance containing all the hybrid-specific request parameters.
	 */
	private HybridSearchRequest(SearchRequest parentRequest, Builder builder) {
		super(parentRequest);

		this.alpha = builder.alpha;
		this.fusionType = builder.fusionType;
		this.options = builder.options;
	}

	/**
	 * Get the alpha value.
	 * @return the alpha value
	 */
	public double getAlpha() {
		return this.alpha;
	}

	/**
	 * Get the fusion type.
	 * @return the fusion type
	 */
	public FusionType getFusionType() {
		return this.fusionType;
	}

	/**
	 * Get the hybrid search options.
	 * @return the hybrid search options
	 */
	@Nullable
	public HybridSearchOptions getOptions() {
		return this.options;
	}

	/**
	 * Create a new builder for HybridSearchRequest.
	 * @return a new builder instance
	 */
	public static HybridSearchRequest.Builder builder() {
		return new Builder();
	}

	/**
	 * Create a builder initialized with values from an existing HybridSearchRequest.
	 * @param originalRequest the request to copy values from
	 * @return a new builder instance
	 */
	public static HybridSearchRequest.Builder from(HybridSearchRequest originalRequest) {
		return builder()
				.query(originalRequest.getQuery())
				.topK(originalRequest.getTopK())
				.similarityThreshold(originalRequest.getSimilarityThreshold())
				.filterExpression(originalRequest.getFilterExpression())
				.alpha(originalRequest.getAlpha())
				.fusionType(originalRequest.getFusionType())
				.options(originalRequest.getOptions());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		HybridSearchRequest that = (HybridSearchRequest) o;
		return Double.compare(that.alpha, alpha) == 0 &&
				fusionType == that.fusionType &&
				Objects.equals(options, that.options);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), alpha, fusionType, options);
	}

	@Override
	public String toString() {
		return "HybridSearchRequest{" +
				"query='" + getQuery() + '\'' +
				", topK=" + getTopK() +
				", similarityThreshold=" + getSimilarityThreshold() +
				", filterExpression=" + getFilterExpression() +
				", alpha=" + alpha +
				", fusionType=" + fusionType +
				", options=" + options +
				'}';
	}

	/**
	 * Builder for creating HybridSearchRequest instances.
	 */
	public static class Builder extends SearchRequest.Builder {

		private double alpha = DEFAULT_ALPHA;
		private FusionType fusionType = DEFAULT_FUSION_TYPE;
		@Nullable private HybridSearchOptions options;

		/**
		 * Set the query text.
		 * @param query text to use for search
		 * @return this builder
		 */
		@Override
		public Builder query(String query) {
			Assert.notNull(query, "Query can not be null");
			super.query(query);
			return this;
		}

		/**
		 * Set the top K results to return.
		 * @param topK the top K value
		 * @return this builder
		 */
		@Override
		public Builder topK(int topK) {
			Assert.isTrue(topK >= 0, "TopK should be positive");
			super.topK(topK);
			return this;
		}

		/**
		 * Set the similarity threshold.
		 * @param threshold the similarity threshold
		 * @return this builder
		 */
		@Override
		public Builder similarityThreshold(double threshold) {
			Assert.isTrue(threshold >= 0 && threshold <= 1, "Similarity threshold must be in [0,1] range");
			super.similarityThreshold(threshold);
			return this;
		}

		/**
		 * Set the similarity threshold to accept all results.
		 * @return this builder
		 */
		@Override
		public Builder similarityThresholdAll() {
			super.similarityThresholdAll();
			return this;
		}

		/**
		 * Set the filter expression as a string.
		 * @param textExpression the filter expression
		 * @return this builder
		 */
		@Override
		public Builder filterExpression(@Nullable String textExpression) {
			if (textExpression != null) {
				super.filterExpression(new FilterExpressionTextParser().parse(textExpression));
			}
			else {
				super.filterExpression((Filter.Expression) null);
			}
			return this;
		}

		/**
		 * Set the filter expression.
		 * @param expression the filter expression
		 * @return this builder
		 */
		@Override
		public Builder filterExpression(@Nullable Filter.Expression expression) {
			super.filterExpression(expression);
			return this;
		}

		/**
		 * Set the alpha value.
		 * @param alpha the alpha value
		 * @return this builder
		 */
		public Builder alpha(double alpha) {
			Assert.isTrue(alpha >= 0.0 && alpha <= 1.0, "Alpha must be between 0.0 and 1.0");
			this.alpha = alpha;
			return this;
		}

		/**
		 * Set the fusion type.
		 * @param fusionType the fusion type
		 * @return this builder
		 */
		public Builder fusionType(FusionType fusionType) {
			Assert.notNull(fusionType, "FusionType must not be null");
			this.fusionType = fusionType;
			return this;
		}

		/**
		 * Set the hybrid search options.
		 * @param options the hybrid search options
		 * @return this builder
		 */
		public Builder options(@Nullable HybridSearchOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Build the HybridSearchRequest instance.
		 * @return the built instance
		 */
		@Override
		public HybridSearchRequest build() {
			return new HybridSearchRequest(super.build(), this);
		}
	}
}
