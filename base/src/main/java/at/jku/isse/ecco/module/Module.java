package at.jku.isse.ecco.module;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 *
 */
public interface Module extends Persistable {

	public Feature[] getPos();

	public Feature[] getNeg();

	public int getCount();

	public void setCount(int count);

	public void incCount();

	public void incCount(int count);


	public Collection<? extends ModuleRevision> getRevisions();

	public ModuleRevision addRevision(FeatureRevision[] pos, Feature[] neg);

	public ModuleRevision getRevision(FeatureRevision[] pos, Feature[] neg);

	public default boolean matchesRevision(FeatureRevision[] pos, Feature[] neg) {
		for (FeatureRevision otherFeatureRevision : pos) {
			boolean found = false;
			for (Feature feature : this.getPos()) {
				if (otherFeatureRevision.getFeature().equals(feature)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		for (Feature otherFeature : neg) {
			boolean found = false;
			for (Feature feature : this.getNeg()) {
				if (otherFeature.equals(feature)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
	}


	public default int getOrder() {
		return this.getPos().length + this.getNeg().length - 1;
	}


	/**
	 * Checks if this module holds on the given configuration.
	 *
	 * @param configuration The configuration to check against.
	 * @return True if the module is contained in the configuration, false otherwise.
	 */
	public default boolean holds(Configuration configuration) {
		// check if all positive features of the module are contained in the configuration
		for (Feature feature : this.getPos()) {
			boolean found = false;
			for (FeatureRevision confFeatureRevision : configuration.getFeatureRevisions()) {
				if (confFeatureRevision.getFeature().equals(feature)) {
					found = true;
					break;
				}
			}
			if (!found) return false;
		}
		// check if no negative features of the module are contained in the configuration
		for (Feature feature : this.getNeg()) {
			for (FeatureRevision confFeatureRevision : configuration.getFeatureRevisions()) {
				if (confFeatureRevision.getFeature().equals(feature)) {
					return false;
				}
			}
		}
		return true;
	}

	public default boolean implies(Module other) {
		// check that all positive features of this are contained in other
		for (Feature thisFeature : this.getPos()) {
			boolean found = false;
			for (Feature otherFeature : other.getPos()) {
				if (thisFeature.equals(otherFeature)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		// check that none of the negative features of this are contained in other
		for (Feature thisFeature : this.getPos()) {
			for (Feature otherFeature : other.getNeg()) {
				if (thisFeature.equals(otherFeature)) {
					return false;
				}
			}
		}
		return true;
	}


	@Override
	public int hashCode();

	@Override
	public boolean equals(Object object);


	public default String getModuleString() {
		String moduleString = Arrays.stream(this.getPos()).map(Feature::toString).collect(Collectors.joining(", "));
		if (this.getNeg().length > 0)
			moduleString += Arrays.stream(this.getNeg()).map(Feature::toString).collect(Collectors.joining(", "));

		return "d^" + this.getOrder() + "(" + moduleString + ")";
	}

	@Override
	public String toString();

}
