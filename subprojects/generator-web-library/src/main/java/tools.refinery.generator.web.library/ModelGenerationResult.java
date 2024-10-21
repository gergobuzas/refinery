/*
 * SPDX-FileCopyrightText: 2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package tools.refinery.generator.web.library;

import org.eclipse.xtext.web.server.IServiceResult;

import java.util.UUID;

public sealed interface ModelGenerationResult extends IServiceResult permits ModelGenerationSuccessResult,
		ModelGenerationErrorResult, ModelGenerationStatusResult {
	UUID uuid();
}
