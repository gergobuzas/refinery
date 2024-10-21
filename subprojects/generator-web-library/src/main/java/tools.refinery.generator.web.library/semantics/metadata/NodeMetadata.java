/*
 * SPDX-FileCopyrightText: 2023-2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package tools.refinery.generator.web.library.semantics.metadata;

public record NodeMetadata(String name, String simpleName, String typeHash, NodeKind kind) implements Metadata {
}
