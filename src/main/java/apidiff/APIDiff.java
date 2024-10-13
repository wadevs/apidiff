package apidiff;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import apidiff.enums.Classifier;
import apidiff.internal.analysis.DiffProcessor;
import apidiff.internal.analysis.DiffProcessorImpl;
import apidiff.internal.service.git.GitFile;
import apidiff.internal.service.git.GitService;
import apidiff.internal.service.git.GitServiceImpl;
import apidiff.internal.util.UtilTools;
import apidiff.internal.visitor.APIVersion;
import apidiff.util.UtilFile;

public class APIDiff implements DiffDetector {

	public static final int COMMITS_TO_PROCESS_LIMIT = 15000;
	public static final int TIME_TO_PROCESS_SECONDS_LIMIT = 3600;

	private String nameProject;

	private String path;

	private String url;

	private Logger logger = LoggerFactory.getLogger(APIDiff.class);

	public APIDiff(final String nameProject, final String url) {
		this.url = url;
		this.nameProject = nameProject;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	@Override
	public Result detectChangeAtCommit(String commitId, Classifier classifierAPI) {
		Result result = new Result();
		try {
			GitService service = new GitServiceImpl();
			Repository repository = service.openRepositoryAndCloneIfNotExists(this.path, this.nameProject, this.url);
			RevCommit commit = service.createRevCommitByCommitId(repository, commitId);
			Result resultByClassifier = this.diffCommit(commit, repository, this.nameProject, classifierAPI);
			result.getChangeType().addAll(resultByClassifier.getChangeType());
			result.getChangeMethod().addAll(resultByClassifier.getChangeMethod());
			result.getChangeField().addAll(resultByClassifier.getChangeField());
		} catch (Exception e) {
			this.logger.error("Error in calculating commitn diff ", e);
		}
		this.logger.info("Finished processing.");
		return result;
	}

	@Override
	public Result detectChangeAllHistory(String branch, List<Classifier> classifiers) throws Exception {
		Result result = new Result();
		GitService service = new GitServiceImpl();
		Repository repository = service.openRepositoryAndCloneIfNotExists(this.path, this.nameProject, this.url);
		RevWalk revWalk = service.createAllRevsWalk(repository, branch);
		// Commits.
		Iterator<RevCommit> i = revWalk.iterator();
		while (i.hasNext()) {
			try {
				RevCommit currentCommit = i.next();
				for (Classifier classifierAPI : classifiers) {
					Result resultByClassifier = this.diffCommit(currentCommit, repository, this.nameProject,
							classifierAPI);
					result.getChangeType().addAll(resultByClassifier.getChangeType());
					result.getChangeMethod().addAll(resultByClassifier.getChangeMethod());
					result.getChangeField().addAll(resultByClassifier.getChangeField());
				}
			} catch (Throwable ex) {
				this.logger.error("In while: " + ex.getMessage());
			}
		}
		this.logger.info("Finished processing.");
		return result;
	}

	public void detectChangeAndOutputToFiles(String branch, List<Classifier> classifiers, String fileName)
			throws Exception {
		int commitCounter = 0;
		long unixTimeStart = System.currentTimeMillis() / 1000L;
		Result result = new Result();
		GitService service = new GitServiceImpl();
		Repository repository = service.openRepositoryAndCloneIfNotExists(this.path, this.nameProject, this.url);
		RevWalk revWalk = service.createAllRevsWalk(repository, branch);
		// Commits.

		{
			int nbTotCommits = 0;
			RevWalk dummyWalk = service.createAllRevsWalk(repository, branch);
			Iterator<RevCommit> dummyCounter = dummyWalk.iterator();
			while (dummyCounter.hasNext()) {
				nbTotCommits++;
				// this.logger.info("Current commits: " + nbTotCommits);
				dummyCounter.next();
			}
			this.logger.info("Total commits: " + nbTotCommits);

			Iterator<RevCommit> i = revWalk.iterator();
			while (i.hasNext()) {
				try {
					this.logger.info("Commit n°" + commitCounter + ".");
					RevCommit currentCommit = i.next();
					for (Classifier classifierAPI : classifiers) {
						// Sometimes crashes in diffcommit, GC or heap exception
						Result resultByClassifier = this.diffCommit(currentCommit, repository, this.nameProject,
								classifierAPI);
						result.getChangeType().addAll(resultByClassifier.getChangeType());
						result.getChangeMethod().addAll(resultByClassifier.getChangeMethod());
						result.getChangeField().addAll(resultByClassifier.getChangeField());
					}
					if (commitCounter % 100 == 0 && commitCounter > 0 || !i.hasNext()) {
						this.logger.info("Commit n°" + commitCounter + ", outputting to file.");
						writeChangesToFile(result, fileName + "_" + unixTimeStart + "_" + commitCounter + "_" + branch);
						result = new Result();
					}
				} catch (Throwable ex) {
					this.logger.error("In while: " + ex.getMessage());
				}
				commitCounter++;

				// Temporary (?) restriction to avoid "endless" processing of huge projects
				long unixTimeNow = System.currentTimeMillis() / 1000L;
				if ((unixTimeNow - unixTimeStart) > TIME_TO_PROCESS_SECONDS_LIMIT) {
					this.logger.error("Timeout in change detector");
					break;
				}
			}

		}
		this.logger.info("Finished processing.");
	}

	private void writeChangesToFile(Result result, String fileName) {
		List<String> listChanges = new ArrayList<String>();
		listChanges.add(
				// "Category;isBreakingChange;Description;Element;ElementType;Path;Class;RevCommit;isDeprecated;containsJavadoc");
				"Category;isBreakingChange;Description;Element;ElementType;Path;Class;RevCommit;isDeprecated;containsJavadoc");
		flattenListsOfChanges(result.getChangeMethod(), listChanges);
		flattenListsOfChanges(result.getChangeField(), listChanges);
		flattenListsOfChanges(result.getChangeType(), listChanges);

		UtilFile.writeFile("dataset/Output/output" + "_" + fileName.replace("/", "-") + ".csv", listChanges);
	}

	private void flattenListsOfChanges(List<Change> changesToAdd, List<String> finalList) {
		for (Change change : changesToAdd) {
			if (change.isBreakingChange()) {
				String changeString = getCsvChangeLineFromChange(change);
				finalList.add(changeString);
			}
		}
	}

	private String getCsvChangeLineFromChange(Change change) {
		return change.getCategory().getDisplayName() + ";"
				+ change.isBreakingChange() + ";"
				+ change.getDescription() + ";"
				+ change.getElement() + ";"
				+ change.getElementType() + ";"
				+ change.getPath() + ";"
				+ change.getClass() + ";"
				+ change.getRevCommit() + ";"
				+ change.isDeprecated() + ";"
				+ change.containsJavadoc();
	}

	@Override
	public Result detectChangeAllHistory(List<Classifier> classifiers) throws Exception {
		return this.detectChangeAllHistory(null, classifiers);
	}

	@Override
	public Result fetchAndDetectChange(List<Classifier> classifiers) {
		Result result = new Result();
		try {
			GitService service = new GitServiceImpl();
			Repository repository = service.openRepositoryAndCloneIfNotExists(this.path, this.nameProject, this.url);
			RevWalk revWalk = service.fetchAndCreateNewRevsWalk(repository, null);
			// Commits.
			Iterator<RevCommit> i = revWalk.iterator();
			while (i.hasNext()) {
				RevCommit currentCommit = i.next();
				for (Classifier classifierAPI : classifiers) {
					Result resultByClassifier = this.diffCommit(currentCommit, repository, this.nameProject,
							classifierAPI);
					result.getChangeType().addAll(resultByClassifier.getChangeType());
					result.getChangeMethod().addAll(resultByClassifier.getChangeMethod());
					result.getChangeField().addAll(resultByClassifier.getChangeField());
				}
			}
		} catch (Exception e) {
			this.logger.error("Error in calculating commit diff ", e);
		}

		this.logger.info("Finished processing.");
		return result;
	}

	@Override
	public Result detectChangeAllHistory(String branch, Classifier classifier) throws Exception {
		return this.detectChangeAllHistory(branch, Arrays.asList(classifier));
	}

	@Override
	public Result detectChangeAllHistory(Classifier classifier) throws Exception {
		return this.detectChangeAllHistory(Arrays.asList(classifier));
	}

	@Override
	public Result fetchAndDetectChange(Classifier classifier) throws Exception {
		return this.fetchAndDetectChange(Arrays.asList(classifier));
	}

	private Result diffCommit(final RevCommit currentCommit, final Repository repository, String nameProject,
			Classifier classifierAPI) throws Exception {
		File projectFolder = new File(UtilTools.getPathProject(this.path, nameProject));
		if (currentCommit.getParentCount() != 0) {// there is at least one parent
			try {
				APIVersion version1 = this.getAPIVersionByCommit(currentCommit.getParent(0).getName(), projectFolder,
						repository, currentCommit, classifierAPI);// old version
				APIVersion version2 = this.getAPIVersionByCommit(currentCommit.getId().getName(), projectFolder,
						repository, currentCommit, classifierAPI); // new version
				DiffProcessor diff = new DiffProcessorImpl();
				return diff.detectChange(version1, version2, repository, currentCommit);
			} catch (Exception e) {
				this.logger.error("Error during checkout [commit=" + currentCommit + "]");
			}
		}
		return new Result();
	}

	private APIVersion getAPIVersionByCommit(String commit, File projectFolder, Repository repository,
			RevCommit currentCommit, Classifier classifierAPI) throws Exception {

		GitService service = new GitServiceImpl();

		// Finding changed files between current commit and parent commit.
		Map<ChangeType, List<GitFile>> mapModifications = service.fileTreeDiff(repository, currentCommit);

		service.checkout(repository, commit);
		return new APIVersion(this.path, projectFolder, mapModifications, classifierAPI);
	}

}
