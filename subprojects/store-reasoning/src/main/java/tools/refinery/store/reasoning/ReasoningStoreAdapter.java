/*
 * SPDX-FileCopyrightText: 2021-2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package tools.refinery.store.reasoning;

import tools.refinery.store.adapter.ModelStoreAdapter;
import tools.refinery.store.model.Model;
import tools.refinery.store.reasoning.representation.AnyPartialSymbol;

import java.util.Collection;

public interface ReasoningStoreAdapter extends ModelStoreAdapter {
	Collection<AnyPartialSymbol> getPartialSymbols();

	Collection<AnyPartialSymbol> getRefinablePartialSymbols();

	Model createInitialModel();

	@Override
	ReasoningAdapter createModelAdapter(Model model);
}
