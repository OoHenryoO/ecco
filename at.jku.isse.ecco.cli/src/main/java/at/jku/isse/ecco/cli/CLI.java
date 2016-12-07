package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.DependencyGraph;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.listener.RepositoryListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.util.Trees;
import org.perf4j.LoggingStopWatch;
import org.perf4j.StopWatch;
import org.perf4j.aop.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class implements all the CLI commands.
 */
public class CLI implements RepositoryListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(CLI.class);

	private EccoService eccoService;


	// # EVENTS ######################################

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {
		System.out.println("READ: " + file);
	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {
		System.out.println("WRITE: " + file);
	}

	@Override
	public void associationSelectedEvent(EccoService service, Association association) {
		System.out.println("SELECTED: [" + association.getId() + "] " + association.getPresenceCondition().getLabel());
	}

	// TODO: other events


	// # CLI #########################################

	public CLI() {
		this.eccoService = new EccoService();
		this.eccoService.detectRepository();
		this.eccoService.addListener(this);
	}

	private void initRepo() {
		if (!this.eccoService.repositoryDirectoryExists())
			throw new EccoException("There is no repository at " + this.eccoService.getRepositoryDir());

		this.eccoService.init();
	}


	/**
	 * TODO:
	 * - ignore files
	 * - file to plugin map
	 * - untracked files: those not yet in file to plugin map
	 * - ignored files: those in ignore list
	 * - changed and unchanged files (during checkout save hash for every checked out file. this needs to be done in base directory, similar to .config or .warnings. for simplicity, initially i could generate .hashes or .ecco/.hashes).
	 * - current configuration: see .config
	 */


	@Profiled // this annotation requires aspectj or spring aop to work.
	public void init() {
		StopWatch stopWatch = new LoggingStopWatch();
		if (this.eccoService.repositoryDirectoryExists()) {
			System.err.println("ERROR: Repository already exists at this location.");
		} else {
			if (this.eccoService.createRepository()) {
				System.out.println("SUCCESS: Repository initialized.");
				this.eccoService.close();
			} else
				System.err.println("ERROR: Error during repository initialization.");
		}
		stopWatch.stop();
	}

	public void status() {
		this.initRepo();

		StringBuffer output = new StringBuffer();

		output.append("Repository Directory: " + this.eccoService.getRepositoryDir() + "\n");
		output.append("Base Directory: " + this.eccoService.getBaseDir() + "\n");

		// TODO: status

		// configuration
		output.append("Current Configuration: " + "\n");

		// files status
		output.append("Unchanged Files:");
		output.append("\n");

		output.append("Changed Files:");
		output.append("\n");

		output.append("Untracked Files:");
		output.append("\n");

		output.append("Ignored Files:");
		output.append("\n");

		System.out.println(output.toString());

		this.eccoService.close();
	}

	public void setProperty(String clientProperty, String value) {
		this.initRepo();

		switch (clientProperty.toLowerCase()) {
			case "basedir":
				Path baseDir = Paths.get(value);
				this.eccoService.setBaseDir(baseDir);
				System.out.println("SUCCESS: SET baseDir=" + baseDir);
				break;
			case "maxorder":
				int maxOrder = Integer.parseInt(value);
				this.eccoService.setMaxOrder(maxOrder);
				System.out.println("SUCCESS: SET maxOrder=" + maxOrder);
				break;
			default:
				System.out.println("ERROR: No property named \"" + clientProperty + "\".");
				break;
		}

		this.eccoService.close();
	}

	public void getProperty(String clientProperty) {
		this.initRepo();

		switch (clientProperty.toLowerCase()) {
			case "basedir":
				System.out.println("SUCCESS: GET baseDir=" + this.eccoService.getBaseDir());
				break;
			case "maxorder":
				System.out.println("SUCCESS: GET maxOrder=" + this.eccoService.getMaxOrder());
				break;
			default:
				System.out.println("ERROR: No property named \"" + clientProperty + "\".");
				break;
		}

		this.eccoService.close();
	}

//	public void addFiles(String pathString) throws EccoException {
//		if (!this.repository.repositoryDirectoryExists())
//			return;
//
//		this.repository.detectRepository();
//		this.repository.init();
//
//		// NOTE: maybe to this in the client service and not in the CLI.
//		try {
//			// collect ecco files
//			Set<Path> eccoFiles = new HashSet<Path>();
//			Files.walk(this.repository.getBaseDir()).forEach(eccoFiles::add);
//
//			// go through files in current folder
//			Files.walk(Paths.get(pathString)).filter(path -> {
//				boolean accept = true;
//				// ignore all ecco files
//				accept = accept && !eccoFiles.contains(path);
//				// ignore directories
//				accept = accept && !Files.isDirectory(path);
//				// ignore all files on the ignore list
////				accept = accept && !this.clientService.getIgnoredFiles().contains(path);
//				return accept;
//			}).forEach(path -> {
////				this.clientService.addTrackedFile(path);
//				System.out.println("ADDED: " + path.toString());
//			});
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//	}
//
//	public void removeFiles(String path) {
//
//	}
//
//	public void ignoreFiles(String path) {
//
//	}

	public void checkout(String configurationString) {
		this.initRepo();

		this.eccoService.checkout(configurationString);

		this.eccoService.close();
	}

	public void commit() {
		this.initRepo();

		this.eccoService.commit();

		this.eccoService.close();
	}

	public void commit(String configurationString) {
		this.initRepo();

		this.eccoService.commit(configurationString);

		this.eccoService.close();
	}

	public void fork(String remoteUriString) {
		URI remoteUri = URI.create(remoteUriString);

		this.eccoService.fork(remoteUri);

		this.eccoService.close();
	}

	public void pull(String remoteName) {
		this.initRepo();

		this.eccoService.pull(remoteName);

		this.eccoService.close();
	}

	public void pull(String remoteName, String excludedFeatureVersionsString) {
		this.initRepo();

		// TODO

		this.eccoService.close();
	}

	public void push(String remoteName) {
		this.initRepo();

		this.eccoService.pull(remoteName);

		this.eccoService.close();
	}

	public void push(String remoteName, String excludedFeatureVersionsString) {
		this.initRepo();

		// TODO

		this.eccoService.close();
	}

	public void fetch(String remoteName) {
		this.initRepo();

		// TODO

		this.eccoService.close();
	}

	public void addRemote(String remoteName, String remoteUri) {
		this.initRepo();

		this.eccoService.addRemote(remoteName, remoteUri);

		this.eccoService.close();
	}

	public void removeRemote(String remoteName) {
		this.initRepo();

		this.eccoService.removeRemote(remoteName);

		this.eccoService.close();
	}

	public void listRemotes() {
		this.initRepo();

		for (Remote remote : this.eccoService.getRemotes()) {
			System.out.println(remote.getName() + ": " + remote.getAddress() + " [" + remote.getType() + "]");
		}

		this.eccoService.close();
	}

	public void showRemote(String remoteName) {
		this.initRepo();

		Remote remote = this.eccoService.getRemote(remoteName);
		if (remote != null) {
			System.out.println(remote.getName() + ": " + remote.getAddress() + " [" + remote.getType() + "]");

			if (remote.getFeatures() != null) {
				for (Feature feature : remote.getFeatures()) {
					System.out.println(feature.toString());
					for (FeatureVersion fv : feature.getVersions()) {
						System.out.println("\t" + fv);
					}
				}
			}
		} else {
			System.out.println("Remote " + remoteName + " does not exist.");
		}

		this.eccoService.close();
	}

	public void listFeatures() {
		this.initRepo();

		for (Feature feature : this.eccoService.getRepository().getFeatures()) {
			System.out.println(feature.toString());
		}

		this.eccoService.close();
	}

	public void showFeature(String featureName) {
		this.initRepo();

		for (Feature feature : this.eccoService.getRepository().getFeatures()) {
			if (feature.getName().equals(featureName)) {
				System.out.println(feature.toString());
				for (FeatureVersion fv : feature.getVersions()) {
					System.out.println("\t" + fv);
				}
			}
		}

		this.eccoService.close();
	}

	public void listTraces() {
		this.initRepo();

		for (Association association : this.eccoService.getRepository().getAssociations()) {
			System.out.println("[" + association.getId() + "] " + association.getPresenceCondition().getLabel());
		}

		this.eccoService.close();
	}

	public void showTraces(String traceId) {
		this.initRepo();

		for (Association association : this.eccoService.getRepository().getAssociations()) {
			if (association.getId() == Integer.valueOf(traceId)) {
				System.out.println("[" + association.getId() + "] " + association.getPresenceCondition().getLabel());
				Trees.print(association.getRootNode());
			}
		}

		this.eccoService.close();
	}

	public void showDependencyGraph() {
		this.initRepo();

		new DependencyGraph(this.eccoService.getRepository().getAssociations()).getGMLString(); // TODO: do this via the repository api

		this.eccoService.close();
	}

	public void setRepoDir(String repoDir) {
		this.eccoService.setRepositoryDir(Paths.get(repoDir));
	}

	public void setBaseDir(String baseDir) {
		this.eccoService.setBaseDir(Paths.get(baseDir));
	}

}
