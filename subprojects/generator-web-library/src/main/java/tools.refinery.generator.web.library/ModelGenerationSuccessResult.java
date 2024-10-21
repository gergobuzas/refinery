/*
 * SPDX-FileCopyrightText: 2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package tools.refinery.generator.web.library;

import com.google.gson.JsonObject;
import tools.refinery.generator.web.library.semantics.metadata.NodeMetadata;
import tools.refinery.generator.web.library.semantics.metadata.RelationMetadata;

import java.util.List;
import java.util.UUID;

public record ModelGenerationSuccessResult(UUID uuid, List<NodeMetadata> nodes, List<RelationMetadata> relations,
										   JsonObject partialInterpretation) implements ModelGenerationResult {
}
