package at.jku.isse.ecco.core;

import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.RootNode;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link Association}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseAssociation implements Association {

	private int id;
	private String name = "";
	private RootNode artifactTreeRoot;
	private PresenceCondition presenceCondition;
	private List<Association> parents = new ArrayList<Association>();

	/**
	 * Constructs a new association.
	 */
	public BaseAssociation() {

	}


	@Override
	public PresenceCondition getPresenceCondition() {
		return this.presenceCondition;
	}

	@Override
	public void setPresenceCondition(PresenceCondition presenceCondition) {
		this.presenceCondition = presenceCondition;
	}

	@Override
	public List<Association> getParents() {
		return this.parents;
	}

	@Override
	public void addParent(Association parent) {
		this.parents.add(parent);
	}

	@Override
	public void removeParent(Association parent) {
		this.parents.remove(parent);
	}

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public void setId(final int id) {
		this.id = id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(final String name) {
		checkNotNull(name);

		this.name = name;
	}

	@Override
	public RootNode getRootNode() {
		return artifactTreeRoot;
	}

	@Override
	public void setRootNode(final RootNode root) {
		this.artifactTreeRoot = root;
		root.setContainingAssociation(this);
	}

	@Override
	public String toString() {
		return String.format("Id: %d, Name: %s, Artifact Tree: %s", this.id, this.name, artifactTreeRoot.toString());
	}

}