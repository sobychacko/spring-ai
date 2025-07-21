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

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.HybridSearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * Extends the base VectorStore interface to provide support for hybrid search.
 * Hybrid search combines vector similarity search with keyword/text search
 * to offer more relevant and robust retrieval.
 */
public interface HybridVectorStore extends VectorStore {

	/**
	 * Performs a hybrid search (combining vector similarity and keyword matching)
	 * based on the provided HybridSearchRequest.
	 * @param request The HybridSearchRequest containing query, fusion parameters, and options.
	 * @return A list of Documents ranked according to the hybrid search algorithm.
	 */
	List<Document> hybridSearch(HybridSearchRequest request);

}
