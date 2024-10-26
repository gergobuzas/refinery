package tools.refinery.language.web.semantics.metadata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import tools.refinery.language.web.xtext.servlet.RuntimeTypeAdapterFactory;

public class RelationMetadataGson {
	public static Gson createGson() {
		RuntimeTypeAdapterFactory<RelationDetail> relationDetailAdapterFactory =
				RuntimeTypeAdapterFactory
						.of(RelationDetail.class, "type") // `type` can be used to determine which implementation to use
						.registerSubtype(ClassDetail.class, "ClassDetail")
						.registerSubtype(ReferenceDetail.class, "ReferenceDetail")
						.registerSubtype(PredicateDetail.class, "PredicateDetail")
						.registerSubtype(OppositeReferenceDetail.class, "OppositeReferenceDetail")
						.registerSubtype(BuiltInDetail.class, "BuiltInDetail")
						.registerSubtype(BasePredicateDetail.class, "BasePredicateDetail");

		return new GsonBuilder()
				.registerTypeAdapterFactory(relationDetailAdapterFactory)
				.create();
	}
}
