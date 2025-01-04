package apidiff;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import com.google.common.base.Throwables;
import com.jcabi.immutable.Array;
import com.jcabi.log.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;

import apidiff.enums.Classifier;
import apidiff.internal.service.git.GitService;
import apidiff.internal.service.git.GitServiceImpl;
import apidiff.util.UtilFile;

import apidiff.util.JavaParserUtility;

public class Experiments {
    // Generated with APIDiff
    public static final int API_NAME_POSITION = 4;
    public static final int API_OUTPUT_TIME_POSITION = 0;
    public static final int BC_REV_COMMIT_TIME_POSITION = 2;
    public static final int BC_REV_COMMIT_ID_POSITION = 1;
    public static final int FILE_NAME_API_NAME_POSITION = 1;

    public static final String ARTIFACT_LINK_PATH = "/artifact/";

    // Generated with Scraper
    public static final String CSV_HEADER_ARTIFACT_TITLE = "ArtifactTitle";
    public static final String CSV_HEADER_ARTIFACT_TITLE_LIST = "ArtifactTitleList";
    public static final String CSV_HEADER_ARTIFACT_LINK = "ArtifactLink";
    public static final String CSV_HEADER_TITLE = "Title";
    public static final String CSV_HEADER_GITHUB_LINK = "GithubLink";
    public static final String CSV_HEADER_GITHUB_LINKS = "GithubLinks";

    // Migration identifier results
    public static final String CSV_HEADER_CLIENT_NAME = "ClientName";
    public static final String CSV_HEADER_API_NAME = "ApiNameCsv";
    public static final String CSV_HEADER_CLIENT_MAIN_HAS_COMMITS = "ClientMainHasCommits";
    public static final String CSV_HEADER_CLIENT_MASTER_HAS_COMMITS = "ClientMasterHasCommits";
    public static final String CSV_HEADER_API_HAS_BCS = "ApiHasBcs";
    public static final String CSV_HEADER_CLIENT_COMMIT_ID = "ClientCommitId";
    public static final String CSV_HEADER_CLIENT_COMMIT_HAS_JAVA_FILES = "ClientCommitHasJavaFiles";
    public static final String CSV_HEADER_CLIENT_HAS_IMPORTS = "JavaFileHasImports";
    public static final String CSV_HEADER_CLIENT_HAS_IMPORTS_OF_BCFILE = "JavaFileHasImportsOfFileContainingBc";
    public static final String CSV_HEADER_CLIENT_BC_ID = "BcIdentifier";
    public static final String CSV_HEADER_BC_CATEGORY = "BcCategory";
    public static final String CSV_HEADER_CLIENT_BC_COMMIT_ID = "BcCommitId";
    public static final String CSV_HEADER_CLIENT_DIFF_CONTAINS_BC_ID = "DiffContainsBcIdentifier";
    public static final String CSV_HEADER_CLIENT_DIFF_LINES_CONTAINS_BC_ID = "DiffLinesContainBcIdentifier";
    public static final String CSV_HEADER_CLIENT_PROCESSING_TIMEOUT = "ProcessingTimeoutOrError";

    // Protocols and extensions
    public static final String GIT_EXTENSION = ".git";
    public static final String GIT_PROTOCOL_GITHUB = "git://github.com/";
    public static final String GIT_SSH_PROTOCOL_GITHUB = "git@github.com:";
    public static final String SCM_PROTOCOL_GITHUB = "scm:" + GIT_SSH_PROTOCOL_GITHUB;
    public static final String HTTPS_PREFIX = "https://";
    public static final String HTTP_PREFIX = "http://";
    public static final String HTTPS_PROTOCOL_GITHUB = HTTPS_PREFIX + "github.com/";
    public static final String HTTP_PROTOCOL_GITHUB = HTTP_PREFIX + "github.com/";

    // Directories
    public static final String DIR_DATASET = "dataset";
    public static final String DIR_CLIENTS_DATASET = DIR_DATASET + "/Clients";
    public static final String DIR_OUTPUT = DIR_DATASET + "/Output";
    public static final String DIR_OUTPUT_MIGRATIONS = DIR_OUTPUT + "/Migrations";
    public static final String DIR_OUTPUT_MIGRATIONS_SUCCESS = DIR_OUTPUT_MIGRATIONS + "/SuccessfulRun";

    public static void main(String[] args) throws Exception {
        // String mode = "process diff"; // process diff
        // String mode = "test/get file names"; // tests -> ok
        // String mode = "test/get file content"; // tests -> ok
        // String mode = "test/get diffs"; // tests -> ok
        // String mode = "test migration"; // tests -> ok
        String mode = "migration process"; // tests ->

        Experiments exp = new Experiments();

        if (mode == "process diff") {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
            LocalDateTime now = LocalDateTime.now();

            String dirToProcess = UtilFile.getAbsolutePath("dataset/ToProcess/");
            String dirProcessed = UtilFile.getAbsolutePath("dataset/Processed/");
            File[] fileList = new File(dirToProcess).listFiles();
            File[] datasetFolderList = new File(UtilFile.getAbsolutePath("dataset")).listFiles();

            List<Map<String, String>> csvMapList = new ArrayList<>();
            for (File file : fileList) {
                if (file.isFile()) {
                    csvMapList.addAll(UtilFile.convertCSVFileToListofMaps(file.getAbsolutePath()));
                }
            }

            // Regroup all the artifacts that share a common git repository
            Map<String, String> gitLinkSharedMap = new HashMap<String, String>();
            for (Map<String, String> map : csvMapList) {
                String[] githubLinks = map.get(CSV_HEADER_GITHUB_LINKS).split(",");
                String nameProject = convertArtifactPathToFilename(map.get(CSV_HEADER_ARTIFACT_LINK));
                String firstLink = githubLinks[0];

                try {
                    // firstLink = githubLinks[0].trim();

                    if (!firstLink.equals(CSV_HEADER_GITHUB_LINKS)) {
                        firstLink = convertGithubLinkToHttps(firstLink);

                        String currentProjectNamesForLink = gitLinkSharedMap.get(firstLink);

                        // Append the current name if there are other artifacts already, use "$" as
                        // separator
                        if (currentProjectNamesForLink != null && currentProjectNamesForLink.length() > 0) {
                            gitLinkSharedMap.put(firstLink, currentProjectNamesForLink + "$" + nameProject);
                        }
                        // Put the first one in otherwise
                        else {
                            gitLinkSharedMap.put(firstLink, nameProject);
                        }
                    }
                } catch (Exception ex) {
                    try {
                        Logger.info(githubLinks, ex.getMessage());
                    } catch (Exception probablyIllegalStateException) {
                        // Illegal reflection call from java 12
                    }
                }
            }

            // Save the mapping file for reusability
            List<String> linkToProjectsMapping = new ArrayList<String>();
            linkToProjectsMapping.add(CSV_HEADER_GITHUB_LINK + ";" + CSV_HEADER_ARTIFACT_TITLE_LIST);

            for (String link : gitLinkSharedMap.keySet()) {
                linkToProjectsMapping.add(link + ";" + gitLinkSharedMap.get(link));
            }

            try {
                // TODO: works as append-to-file atm -> change it to override ?
                UtilFile.writeFile(DIR_DATASET + "/linkToProjectsMapping" + ".csv", linkToProjectsMapping);
            } catch (Exception ex) {
                Logger.info(linkToProjectsMapping, ex.getMessage());
            }

            // for (Map<String, String> map : csvMapList) {
            // String[] githubLinks = map.get("GithubLinks").split(",");
            // // String nameProject = map.get("ArtifactTitle");
            // try {
            // String firstLink = githubLinks[0].trim();
            // // System.out.println(firstLink);

            // // Use the trimmed github link as project name
            // if (!firstLink.equals(CSV_HEADER_GITHUB_LINKS)) {
            // exp.process(dtf.format(now),
            // trimGithubLinkToFilename(convertGithubLinkToHttps(firstLink)),
            // firstLink + ".git");
            // }
            // } catch (Exception ex) {
            // System.out.println(ex.getMessage());
            // }
            // }

            // Iterate over the links keySet to prevent redundant computation
            for (String link : gitLinkSharedMap.keySet()) {
                try {
                    boolean datasetContainsFolder = false;
                    for (File datasetFolder : datasetFolderList) {
                        if (datasetFolder.isDirectory() && datasetFolder
                                .getName().equals(trimGithubLinkToFilename(convertGithubLinkToHttps(link)))) {
                            datasetContainsFolder = true;
                            break;
                        }
                    }
                    // Use the trimmed github link as project name
                    if (!datasetContainsFolder && !link.equals(CSV_HEADER_GITHUB_LINKS)) {
                        exp.process(dtf.format(now), trimGithubLinkToFilename(convertGithubLinkToHttps(link)), link);
                    }
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }

            for (File file : fileList) {
                if (file.isFile()) {
                    Files.move(Paths.get(dirToProcess + "/" + file.getName()),
                            Paths.get(dirProcessed + "/" + file.getName()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Single call tests
            // exp.process(dtf.format(now), "MPAndroidChart",
            // "https://github.com/PhilJay/MPAndroidChart.git");

            // exp.process(dtf.format(now), "mockito",
            // "https://github.com/mockito/mockito.git");

            // exp.process(dtf.format(now), "junit-engine",
            // "https://github.com/junit-team/junit5.git");
        } else if (mode == "test/get file names") {
            String projectName = "MPAndroidChart";
            String clientGitUrl = "https://github.com/PhilJay/MPAndroidChart.git";

            GitService service = new GitServiceImpl();
            Repository repository = service.openRepositoryAndCloneIfNotExists(
                    UtilFile.getAbsolutePath("dataset"), projectName, clientGitUrl);

            RevWalk rw = new RevWalk(repository);
            ObjectId head = repository.resolve("934b20bd5f2ab87a201740a6cdc578704c3d08e6");
            RevCommit commit = rw.parseCommit(head);
            RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);
            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
            for (DiffEntry diff : diffs) {
                System.out.println(MessageFormat.format("{0}, {1}, {2}", diff.getChangeType().name(),
                        diff.getNewMode().getBits(), diff.getNewPath()));
            }

            rw.close();
            df.close();
        } else if (mode == "test/get file content") {
            String projectName = "MPAndroidChart";
            String clientGitUrl = "https://github.com/PhilJay/MPAndroidChart.git";

            GitService service = new GitServiceImpl();
            Repository repository = service.openRepositoryAndCloneIfNotExists(
                    UtilFile.getAbsolutePath("dataset"), projectName, clientGitUrl);

            RevWalk rw = new RevWalk(repository);
            RevCommit commitToCheck = rw.parseCommit(repository.resolve("934b20bd5f2ab87a201740a6cdc578704c3d08e6"));

            List<Pair<String, String>> codeContents = exp.getJavaFileContentInCommitWithTreeWalk(repository,
                    commitToCheck);
            for (Pair<String, String> fileContent : codeContents) {
                String codeContent = fileContent.getValue();
                List<String> imports = ImportMatching.getImports(codeContent);
                for (String importString : imports) {
                    System.out.println(MessageFormat.format("Import: {0}", importString));
                }
            }

            rw.close();
        } else if (mode == "test/get diffs") {
            String projectName = "MPAndroidChart";
            String clientGitUrl = "https://github.com/PhilJay/MPAndroidChart.git";

            GitService service = new GitServiceImpl();
            Repository repository = service.openRepositoryAndCloneIfNotExists(
                    UtilFile.getAbsolutePath("dataset"), projectName, clientGitUrl);

            RevWalk rw = new RevWalk(repository);
            RevCommit commitToCheck = rw.parseCommit(repository.resolve("934b20bd5f2ab87a201740a6cdc578704c3d08e6"));

            List<Pair<String, String>> allDiffs = exp.getAllDiffsWithParentFromCommit(repository, commitToCheck);
            allDiffs.forEach(
                    p -> System.out.println(MessageFormat.format("File: {0}, \nDiff: {1}", p.getKey(), p.getValue())));

            rw.close();
        } else if (mode == "test migration") {
            // Test case broken by updating for multiple usages
            exp.migrationIdentifier("https://github.com/wadevs/DummyMaven", "DummyMaven", "MPAndroidChart", "", null);
        } else if (mode == "migration process") {
            String dirToProcessUsages = UtilFile.getAbsolutePath(DIR_DATASET + "/ToProcess/Usages");
            String dirToProcessUsagesDone = dirToProcessUsages + "/Done";
            // String dirProcessed = UtilFile.getAbsolutePath("dataset/Processed/");
            File[] fileList = new File(dirToProcessUsages).listFiles();

            File outputMigrationsDirSuccess = new File(DIR_OUTPUT_MIGRATIONS_SUCCESS);
            if (!outputMigrationsDirSuccess.exists()) {
                outputMigrationsDirSuccess.mkdirs();
            }
            File dirToProcessUsagesDoneFile = new File(dirToProcessUsagesDone);
            if (!dirToProcessUsagesDoneFile.exists()) {
                dirToProcessUsagesDoneFile.mkdirs();
            }

            for (File usagesFile : fileList) {
                if (usagesFile.isFile()) {
                    // apiName is the 5th element in the filename by construction
                    String apiName = usagesFile.getName().split("_")[API_NAME_POSITION];
                    // apiOutputTime is the 1st element in the filename by construction
                    String apiOutputTime = usagesFile.getName().split("_")[API_OUTPUT_TIME_POSITION];

                    // Dir creation no longer needed
                    // File outputApiDir = new File("dataset/Output/" + apiName);
                    // if (!outputApiDir.exists()) {
                    // outputApiDir.mkdirs();
                    // }

                    List<Map<String, String>> usagesCsvMapList = new ArrayList<>();
                    usagesCsvMapList.addAll(UtilFile.convertCSVFileToListofMaps(usagesFile.getAbsolutePath()));

                    for (Map<String, String> usageCsvMap : usagesCsvMapList) {
                        try {
                            // Should only have one link
                            String githubLink = convertGithubLinkToHttps(
                                    usageCsvMap.get(CSV_HEADER_GITHUB_LINKS).trim().split(" ")[0]);
                            // String projectName =
                            // convertArtifactPathToFilename(usageCsvMap.get(CSV_HEADER_ARTIFACT_LINK));
                            String projectName = trimGithubLinkToFilename(githubLink);

                            // Dir creation no longer needed
                            File outputApiClientDir = null;// new File(outputApiDir + "/" + projectName);
                            // if (!outputApiClientDir.exists()) {
                            // outputApiClientDir.mkdirs();
                            // }

                            exp.migrationIdentifier(githubLink, projectName, apiName, apiOutputTime,
                                    outputApiClientDir);
                        } catch (Exception e) {
                            Logger.info("usagesCsvMapList loop", e.getMessage());
                        }
                    }

                    // Move the files to know where the to restart
                    Files.move(Paths.get(usagesFile.getPath()),
                            Paths.get(dirToProcessUsagesDone + "/" + usagesFile.getName()),
                            StandardCopyOption.REPLACE_EXISTING);

                    File[] SuccessRunFiles = new File(DIR_OUTPUT_MIGRATIONS).listFiles();
                    for (File migrationFile : SuccessRunFiles) {
                        if (migrationFile.isFile()) {
                            Files.move(Paths.get(migrationFile.getPath()),
                                    Paths.get(DIR_OUTPUT_MIGRATIONS_SUCCESS + "/" + migrationFile.getName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }

                }
            }
        }
    }

    private void process(String formattedNow, String nameProject, String url) throws Exception {
        APIDiff diff = new APIDiff(nameProject, url);
        diff.setPath(UtilFile.getAbsolutePath("dataset"));

        // The principal branch could be either master or main
        diff.detectChangeAndOutputToFiles("master", Arrays.asList(Classifier.API), nameProject);
        diff.detectChangeAndOutputToFiles("main", Arrays.asList(Classifier.API), nameProject);
    }

    private void migrationIdentifier(String clientGitUrl, String projectName, String apiName, String apiOutputTime,
            File migrationLogOutputDir) {
        // String clientGitUrl = "";
        int potentialMigrationCounter = 0;

        // Result parameters for final CSV file
        String clientNameCsv = projectName;
        String apiNameCsv = apiName;
        boolean clientMainHasCommits = false;
        boolean clientMasterHasCommits = false;
        boolean apiHasBcs = false;
        String commitId = "";
        boolean commitHasJavaFiles = false;
        boolean javaFileHasImports = false;
        boolean javaFileHasImportsOfFileContainingBc = false;
        String bcIdentifier = "";
        String bcCommitId = "";
        String bcCategory = "";
        boolean diffContainsBcIdentifier = false;
        boolean diffLinesContainBcIdentifier = false;
        boolean processingTimeout = false;

        // Maps to be logged as final results
        List<Map<String, String>> migrationDetectionResults = new ArrayList<>();

        try {
            File clientsDataset = new File(DIR_CLIENTS_DATASET);
            if (!clientsDataset.exists()) {
                clientsDataset.mkdirs();
            }

            GitService service = new GitServiceImpl();
            Repository repository = service.openRepositoryAndCloneIfNotExists(
                    UtilFile.getAbsolutePath(DIR_CLIENTS_DATASET), projectName, clientGitUrl);
            RevWalk revWalk = service.createAllRevsWalk(repository, "main");

            int nbCommitsInBranch = nbCommitsInBranch(service, repository, "main");
            if (nbCommitsInBranch <= 0) {
                revWalk = service.createAllRevsWalk(repository, "master");
                nbCommitsInBranch = nbCommitsInBranch(service, repository, "master");

                clientMasterHasCommits = (nbCommitsInBranch >= 0);
            } else {
                clientMainHasCommits = true;
            }

            // restrict processing time to apidiff.TIME_TO_PROCESS_SECONDS_LIMIT
            long unixTimeStart = System.currentTimeMillis() / 1000L;

            String bcsDir = UtilFile.getAbsolutePath(DIR_OUTPUT);
            File[] fileList = new File(bcsDir).listFiles();

            // Extract the gitlink-to-foldername mapping
            Map<String, String> datasetMap = new HashMap<>();
            try {
                File datasetMappingFile = new File(
                        UtilFile.getAbsolutePath(DIR_DATASET + "/linkToProjectsMapping.csv"));
                datasetMap = UtilFile.convertCSVFileToMap(datasetMappingFile.getAbsolutePath());
            } catch (Exception ex) {
                Logger.info(this, ex.getMessage());
            }

            String apiMappedLink = "";
            List<Map<String, String>> bcsMapList = new ArrayList<>();
            for (File file : fileList) {

                for (String key : datasetMap.keySet()) {
                    String artifactWithOrgListString = datasetMap.get(key);

                    if (artifactWithOrgListString != null) {
                        String[] artifactWithOrgList = artifactWithOrgListString.split("$");

                        for (String artifactWithOrg : artifactWithOrgList) {
                            if (artifactWithOrg.equals(apiName)) {
                                apiMappedLink = key;
                                break;
                            }
                        }
                    }

                    if (apiMappedLink.length() > 0) {
                        break;
                    }
                }

                String apiFolderName = trimGithubLinkToFilename(convertGithubLinkToHttps(apiMappedLink));
                if (file.isFile() && file.getName().split("_")[FILE_NAME_API_NAME_POSITION].equals(apiFolderName)) {
                    bcsMapList.addAll(UtilFile.convertCSVFileToListofMaps(file.getAbsolutePath()));
                }
            }

            clientNameCsv = projectName;
            apiName = apiMappedLink;

            // TODO: comment
            apiHasBcs = (bcsMapList.size() > 0);

            int clientCommitCounter = 0;

            // putMapInResultList(migrationDetectionResults, clientNameCsv, apiNameCsv,
            // clientMainHasCommits,
            // clientMasterHasCommits, apiHasBcs, commitId, commitHasJavaFiles,
            // javaFileHasImports,
            // javaFileHasImportsOfFileContainingBc, bcIdentifier, bcCommitId,
            // diffContainsBcIdentifier,
            // diffLinesContainBcIdentifier);

            // Only process commits if apiHasBcs == true (...?)
            if (apiHasBcs) {
                Iterator<RevCommit> i = revWalk.iterator();
                while (i.hasNext()) {
                    RevCommit currentClientCommit = i.next();

                    commitId = currentClientCommit.toString();

                    // Granularity: commit time
                    int clientCommitTime = currentClientCommit.getCommitTime();

                    // TODO: Probably not needed anymore
                    // List<String> javaFileNamesInCommit = getJavaFileNamesInCommit(repository,
                    // currentClientCommit); // get
                    // files
                    // in
                    // commit

                    List<Pair<String, String>> diffsForFilesInCommit = getAllDiffsWithParentFromCommit(repository,
                            currentClientCommit);

                    List<Pair<String, String>> javaFileNamesAndContentInCommit = getJavaFileContentInCommitWithTreeWalk(
                            repository, currentClientCommit);

                    // Extract the time-filtered BCs
                    List<Map<String, String>> BcTimeFiltered = new ArrayList<Map<String, String>>();
                    for (Map<String, String> bcMap : bcsMapList) {
                        String[] revCommit = bcMap.get("RevCommit").split(" ");

                        try {
                            int changeCommitTime = Integer.parseInt(revCommit[BC_REV_COMMIT_TIME_POSITION]);
                            if (changeCommitTime < clientCommitTime) {
                                BcTimeFiltered.add(bcMap);
                            }
                        } catch (Exception e) {
                            // Ignore invalid data
                        }

                    }

                    // TODO: comment
                    if (commitHasJavaFiles = (javaFileNamesAndContentInCommit.size() > 0)) {
                        for (Pair<String, String> javaFileNameAndContent : javaFileNamesAndContentInCommit) {

                            String codeContent = javaFileNameAndContent.getValue();

                            List<Map<String, String>> importBCs = new ArrayList<Map<String, String>>();
                            List<String> imports = ImportMatching.getImports(codeContent);

                            // TODO: comment
                            if (javaFileHasImports = (imports.size() > 0)) {
                                for (String importStr : imports) {
                                    for (Map<String, String> bcMap : BcTimeFiltered) {
                                        String path = bcMap.get("Path");
                                        if (path.contains(importStr.replace("*", ""))) {
                                            importBCs.add(bcMap);

                                            try {
                                                Logger.info("import matcher", path);
                                            } catch (IllegalStateException e) {
                                                // Ignore (comes from java 12+)
                                            }
                                        }
                                    }
                                }

                                // Type formatting from List<Map<String, String>> to List<String>
                                List<String> matchedImports = new ArrayList<String>();
                                for (Map<String, String> bcMap : importBCs) {
                                    matchedImports.add(bcMap.get("Path"));
                                }

                                // TODO: comment
                                if (javaFileHasImportsOfFileContainingBc = (matchedImports.size() > 0)) {
                                    String diffsForFile = "";
                                    for (Pair<String, String> diffs : diffsForFilesInCommit) {
                                        if (diffs.getKey().equals(javaFileNameAndContent.getKey())) {
                                            diffsForFile = diffs.getValue();
                                            // TODO: check influence of break here,may need append if more that one
                                            // diffs
                                            // per file
                                            break;
                                        }
                                    }

                                    if (diffsForFile.length() > 0) {
                                        for (Map<String, String> bcMap : importBCs) {
                                            try {
                                                bcCommitId = bcMap.get("RevCommit")
                                                        .split(" ")[BC_REV_COMMIT_ID_POSITION];

                                                bcCategory = bcMap.get("Category");

                                                TypeDeclaration typeDec = JavaParserUtility.parseExpression(
                                                        // "public class X {" + bcMap.get("Element") + "{}}",
                                                        // false);
                                                        bcMap.get("Element") + ";", false);

                                                // String methodName = bcMap.get("Element");
                                                String element = "";

                                                // typeDec.accept(new ASTVisitor(true) {
                                                // @Override
                                                // public boolean visit(MethodDeclaration node) {
                                                // System.out.println(node.getName().toString());
                                                // node.getName().toString();
                                                // return false;
                                                // }
                                                // });

                                                // TODO: if/else for method, field, type change
                                                List<Object> bodyDec = typeDec.bodyDeclarations();
                                                // if (bodyDec.size() > 0
                                                // && bodyDec.getFirst() instanceof MethodDeclaration) {
                                                // element = ((MethodDeclaration)
                                                // typeDec.bodyDeclarations().getFirst())
                                                // .getName()
                                                // .toString();
                                                // if (element.equals("setCenterText")) {
                                                // System.out.println(element);
                                                // // System.out.println(diffsForFile);
                                                // }
                                                // }

                                                if (bodyDec.size() > 0) {
                                                    Object dec = bodyDec.getFirst();
                                                    if (dec instanceof MethodDeclaration) {
                                                        element = ((MethodDeclaration) typeDec.bodyDeclarations()
                                                                .getFirst())
                                                                .getName()
                                                                .toString();
                                                    } else if (dec instanceof FieldDeclaration) {
                                                        element = ((FieldDeclaration) typeDec.bodyDeclarations()
                                                                .getFirst())
                                                                .fragments()
                                                                .getFirst()
                                                                .toString();
                                                    } else if (dec instanceof TypeDeclaration) {
                                                        element = ((TypeDeclaration) typeDec.bodyDeclarations()
                                                                .getFirst())
                                                                .getName()
                                                                .toString();
                                                    }
                                                }

                                                bcIdentifier = element;
                                                // TODO: comment
                                                // We don't want to check if "" (empty string) belongs to the files,
                                                // always true
                                                if (element.length() > 0) {
                                                    if (diffContainsBcIdentifier = (diffsForFile.contains(element))) {

                                                        String[] relevantLines = diffsForFile
                                                                .split(System.lineSeparator());
                                                        List<String> matchedLines = new ArrayList<String>();

                                                        List<String> addedLines = new ArrayList<>();
                                                        for (String line : relevantLines) {
                                                            if (line.startsWith("+")) {
                                                                addedLines.add(line);
                                                                if (line.contains(element)) {
                                                                    matchedLines.add(line);
                                                                }
                                                            }
                                                        }

                                                        List<String> removedLines = new ArrayList<>();
                                                        for (String line : relevantLines) {
                                                            if (line.startsWith("-")) {
                                                                removedLines.add(line);
                                                                if (line.contains(element)) {
                                                                    matchedLines.add(line);
                                                                }
                                                            }
                                                        }

                                                        // List<Token> tokenList = JavaParserUtility
                                                        // // .tokensToAST("public class X {" + bcMap.get("Element") +
                                                        // "{}}",
                                                        // cUnit);
                                                        // .tokensToAST(bcMap.get("Element") + ";", cUnit);
                                                        // if (diffsForFile.contains(element)) {
                                                        // // System.out.println("match");
                                                        // // log migration
                                                        // List<String> diffsAsList = new ArrayList<String>();
                                                        // diffsAsList.add(diffsForFile);

                                                        // try {
                                                        // UtilFile.writeFile(
                                                        // migrationLogOutputDir + "/" + apiName + "_" + projectName +
                                                        // "_"
                                                        // + potentialMigrationCounter + ".txt",
                                                        // diffsAsList);
                                                        // potentialMigrationCounter++;
                                                        // } catch (Exception ex) {
                                                        // try {
                                                        // Logger.info(projectName, ex.getMessage());
                                                        // } catch (IllegalStateException e) {
                                                        // // Ignore (comes from java 12+)
                                                        // }
                                                        // }
                                                        // }

                                                        if (diffLinesContainBcIdentifier = matchedLines.size() > 0) {
                                                            putMapInResultList(migrationDetectionResults, clientNameCsv,
                                                                    apiNameCsv, clientMainHasCommits,
                                                                    clientMasterHasCommits,
                                                                    apiHasBcs, commitId, commitHasJavaFiles,
                                                                    javaFileHasImports,
                                                                    javaFileHasImportsOfFileContainingBc, bcIdentifier,
                                                                    bcCategory, bcCommitId, diffContainsBcIdentifier,
                                                                    diffLinesContainBcIdentifier, processingTimeout);
                                                            diffLinesContainBcIdentifier = false;
                                                        }
                                                    } else {
                                                        putMapInResultList(migrationDetectionResults, clientNameCsv,
                                                                apiNameCsv, clientMainHasCommits,
                                                                clientMasterHasCommits,
                                                                apiHasBcs, commitId, commitHasJavaFiles,
                                                                javaFileHasImports,
                                                                javaFileHasImportsOfFileContainingBc, bcIdentifier,
                                                                bcCategory, bcCommitId, diffContainsBcIdentifier,
                                                                diffLinesContainBcIdentifier, processingTimeout);
                                                    }
                                                } else {
                                                    putMapInResultList(migrationDetectionResults, clientNameCsv,
                                                            apiNameCsv, clientMainHasCommits,
                                                            clientMasterHasCommits,
                                                            apiHasBcs, commitId, commitHasJavaFiles,
                                                            javaFileHasImports,
                                                            javaFileHasImportsOfFileContainingBc, bcIdentifier,
                                                            bcCategory, bcCommitId, diffContainsBcIdentifier,
                                                            diffLinesContainBcIdentifier, processingTimeout);
                                                }
                                            } catch (Exception ex) {
                                                try {
                                                    Logger.info(projectName, ex.getMessage());
                                                } catch (IllegalStateException e) {
                                                    // Ignore (comes from java 12+)
                                                }
                                            }
                                        }

                                    }

                                } else {
                                    putMapInResultList(migrationDetectionResults, clientNameCsv, apiNameCsv,
                                            clientMainHasCommits,
                                            clientMasterHasCommits, apiHasBcs, commitId, commitHasJavaFiles,
                                            javaFileHasImports,
                                            javaFileHasImportsOfFileContainingBc, bcIdentifier, bcCategory, bcCommitId,
                                            diffContainsBcIdentifier, diffLinesContainBcIdentifier, processingTimeout);
                                }
                            } else {
                                putMapInResultList(migrationDetectionResults, clientNameCsv, apiNameCsv,
                                        clientMainHasCommits,
                                        clientMasterHasCommits, apiHasBcs, commitId, commitHasJavaFiles,
                                        javaFileHasImports,
                                        javaFileHasImportsOfFileContainingBc, bcIdentifier, bcCategory, bcCommitId,
                                        diffContainsBcIdentifier, diffLinesContainBcIdentifier, processingTimeout);
                            }
                        }
                    } else {
                        putMapInResultList(migrationDetectionResults, clientNameCsv, apiNameCsv, clientMainHasCommits,
                                clientMasterHasCommits, apiHasBcs, commitId, commitHasJavaFiles, javaFileHasImports,
                                javaFileHasImportsOfFileContainingBc, bcIdentifier, bcCategory, bcCommitId,
                                diffContainsBcIdentifier, diffLinesContainBcIdentifier, processingTimeout);
                    }
                    clientCommitCounter++;
                    // Temporary (?) restriction to avoid "endless" processing of huge projects
                    long unixTimeNow = System.currentTimeMillis() / 1000L;
                    if ((unixTimeNow - unixTimeStart) > APIDiff.TIME_TO_PROCESS_SECONDS_LIMIT) {
                        Logger.error(this, "Timeout in migration identifier");
                        processingTimeout = true;
                        break;
                    }
                }
            } else {
                // Initial case if no BCs in this API for this client
                putMapInResultList(migrationDetectionResults, clientNameCsv, apiNameCsv, clientMainHasCommits,
                        clientMasterHasCommits, apiHasBcs, commitId, commitHasJavaFiles, javaFileHasImports,
                        javaFileHasImportsOfFileContainingBc, bcIdentifier, bcCategory, bcCommitId,
                        diffContainsBcIdentifier,
                        diffLinesContainBcIdentifier, processingTimeout);
            }

            if (migrationDetectionResults.size() == 0) {
                putMapInResultList(migrationDetectionResults, clientNameCsv, apiNameCsv, clientMainHasCommits,
                        clientMasterHasCommits, apiHasBcs, commitId, commitHasJavaFiles, javaFileHasImports,
                        javaFileHasImportsOfFileContainingBc, bcIdentifier, bcCategory, bcCommitId,
                        diffContainsBcIdentifier,
                        diffLinesContainBcIdentifier, processingTimeout);
            }

            outputListMapToFile(System.currentTimeMillis() / 1000L, projectName, migrationDetectionResults);
        } catch (Throwable ex) {
            try {
                Logger.error(projectName, ex.getMessage());

                // Log at least one result line in case of heap memory error
                putMapInResultList(migrationDetectionResults, clientNameCsv, apiNameCsv, clientMainHasCommits,
                        clientMasterHasCommits, apiHasBcs, commitId, commitHasJavaFiles, javaFileHasImports,
                        javaFileHasImportsOfFileContainingBc, bcIdentifier, bcCategory, bcCommitId,
                        diffContainsBcIdentifier,
                        diffLinesContainBcIdentifier, true);

                try {
                    outputListMapToFile(System.currentTimeMillis() / 1000L, projectName,
                            migrationDetectionResults);
                } catch (Throwable e) {
                    try {
                        List<String> msgList = new ArrayList<String>();
                        msgList.add(e.getMessage());

                        UtilFile.writeFile(UtilFile.getAbsolutePath(
                                DIR_OUTPUT_MIGRATIONS + "/POTENTIAL_HEAP_ERROR" + projectName + "_"
                                        + ".csv"),
                                msgList);
                    } catch (Exception ex2) {
                        Logger.info("Migration result writefile", ex2.getMessage());
                    }
                }

            } catch (IllegalStateException e) {
                // Ignore (comes from java 12+)
            }

        }
    }

    private List<Pair<String, String>> getAllDiffsWithParentFromCommit(Repository repository, RevCommit commit) {
        List<Pair<String, String>> pathsWithDiffs = new ArrayList<Pair<String, String>>();

        try {
            RevWalk rw = new RevWalk(repository);
            RevCommit parent = rw.parseCommit(commit.getParent(0).getId());

            OutputStream out = new ByteArrayOutputStream();
            DiffFormatter df = new DiffFormatter(out);

            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);
            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());

            for (DiffEntry diff : diffs) {
                out = new ByteArrayOutputStream();
                try (DiffFormatter formatter = new DiffFormatter(out)) {
                    formatter.setRepository(repository);
                    formatter.format(diff);

                    String fileDiff = out.toString();

                    pathsWithDiffs.add(Pair.of(diff.getNewPath(), fileDiff));

                    out.close();
                }
            }

            rw.close();
            df.close();
        } catch (Exception ex) {
            Logger.info(ex, ex.getMessage());
        }

        return pathsWithDiffs;
    }

    private List<String> getJavaFileNamesInCommit(Repository repository, RevCommit commit) {
        List<String> fileNames = new ArrayList<String>();

        try {
            RevWalk rw = new RevWalk(repository);
            RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);
            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
            for (DiffEntry diff : diffs) {
                String fileNewPath = diff.getNewPath();
                if (fileNewPath.contains(".java")) {
                    System.out.println(MessageFormat.format("{0}, {1}, {2}", diff.getChangeType().name(),
                            diff.getNewMode().getBits(), fileNewPath));
                    fileNames.add(fileNewPath);
                }
            }

            rw.close();
            df.close();
        } catch (Exception ex) {
            Logger.info(ex, ex.getMessage());
        }

        return fileNames;
    }

    private List<Pair<String, String>> getJavaFileContentInCommitWithTreeWalk(Repository repository,
            RevCommit commitToCheck) {
        List<Pair<String, String>> filesContent = new ArrayList<>();
        RevWalk rw = new RevWalk(repository);
        try (TreeWalk tw = new TreeWalk(repository)) {
            tw.addTree(commitToCheck.getTree());
            tw.addTree(new DirCacheIterator(repository.readDirCache()));
            tw.addTree(new FileTreeIterator(repository));
            tw.setRecursive(true);
            while (tw.next()) {
                if (tw.getPathString().contains(".java")) {
                    try {
                        ObjectId objectId = tw.getObjectId(0);
                        ObjectLoader loader = repository.open(objectId);
                        String content = new String(loader.getBytes());

                        filesContent.add(Pair.of(tw.getPathString(), content));
                    } catch (Exception e) {
                        try {
                            Logger.info(e, e.getMessage());
                        } catch (Exception probablyIllegalStateException) {
                            // Illegal reflection call from java 12
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Logger.info(ex, ex.getMessage());
        }
        rw.close();

        return filesContent;
    }

    public int nbCommitsInBranch(GitService service, Repository repository, String branch) {
        int nbTotCommits = 0;
        try {
            RevWalk dummyWalk = service.createAllRevsWalk(repository, branch);
            Iterator<RevCommit> dummyCounter = dummyWalk.iterator();
            while (dummyCounter.hasNext()) {
                nbTotCommits++;
                // this.logger.info("Current commits: " + nbTotCommits);
                dummyCounter.next();
            }
            Logger.info(this, "Total commits: " + nbTotCommits);
        } catch (Exception ex) {
            try {
                Logger.info(ex, ex.getMessage());
            } catch (Exception probablyIllegalStateException) {
                // Illegal reflection call from java 12
            }
        }
        return nbTotCommits;
    }

    private static String convertGithubLinkToHttps(String linkToConvert) {
        String converted = linkToConvert.trim();

        // Convert git@git... and scm:git@git... and git://...
        if (converted.contains(SCM_PROTOCOL_GITHUB)) {
            converted = converted.replace(SCM_PROTOCOL_GITHUB, HTTPS_PROTOCOL_GITHUB);
        } else if (converted.contains(GIT_SSH_PROTOCOL_GITHUB)) {
            converted = converted.replace(GIT_SSH_PROTOCOL_GITHUB, HTTPS_PROTOCOL_GITHUB);
        } else if (converted.contains(GIT_PROTOCOL_GITHUB)) {
            converted = converted.replace(GIT_PROTOCOL_GITHUB, HTTPS_PROTOCOL_GITHUB);
        }

        // Add .git at the end if not present already
        if (!converted.substring(converted.length() - 4).equals(GIT_EXTENSION)) {
            converted += GIT_EXTENSION;
        }

        return converted;
    }

    private static String convertArtifactPathToFilename(String artifactPath) {
        String converted = artifactPath.trim().replace(ARTIFACT_LINK_PATH, "").replace("/", "-");

        return converted;
    }

    private static String replaceStringSlashGit(String input, String toReplace) {
        String replaced = input.trim().replace(toReplace, "").replace("/", "-");

        if (replaced.substring(replaced.length() - 4).equals(GIT_EXTENSION)) {
            replaced = replaced.substring(0, replaced.length() - 4);
        }

        return replaced;
    }

    private static String trimGithubLinkToFilename(String link) throws Exception {
        String trimmed = "";
        if (link.contains(HTTPS_PROTOCOL_GITHUB)) {
            trimmed = replaceStringSlashGit(link, HTTPS_PROTOCOL_GITHUB);
        } else if (link.contains(HTTP_PROTOCOL_GITHUB)) {
            trimmed = replaceStringSlashGit(link, HTTP_PROTOCOL_GITHUB);
        }

        if (trimmed.length() <= 0) {
            throw new Exception("Invalid link format");
        }

        return trimmed;
    }

    private static void putMapInResultList(List<Map<String, String>> resultList, String clientName, String apiName,
            boolean clientMainHasCommits, boolean clientMasterHasCommits,
            boolean apiHasBcs, String clientCommitId, boolean clientCommitHasJavaFiles, boolean javaFileHasImports,
            boolean javaFileHasImportsOfFileContainingBc, String bcIdentifier, String bcCategory,
            String bcCommitId, boolean diffContainsBcIdentifier, boolean diffLinesContainBcIdentifier,
            boolean processingTimeout) {
        Map<String, String> mapToAdd = new HashMap<String, String>();

        if (!diffContainsBcIdentifier
                && diffLinesContainBcIdentifier) {
            System.out.println("WTF");
        }

        mapToAdd.put(CSV_HEADER_CLIENT_NAME, clientName);
        mapToAdd.put(CSV_HEADER_API_NAME, apiName);
        mapToAdd.put(CSV_HEADER_CLIENT_MAIN_HAS_COMMITS, (clientMainHasCommits ? "true" : "false"));
        mapToAdd.put(CSV_HEADER_CLIENT_MASTER_HAS_COMMITS, (clientMasterHasCommits ? "true" : "false"));
        mapToAdd.put(CSV_HEADER_API_HAS_BCS, (apiHasBcs ? "true" : "false"));
        mapToAdd.put(CSV_HEADER_CLIENT_COMMIT_ID, clientCommitId);
        mapToAdd.put(CSV_HEADER_CLIENT_COMMIT_HAS_JAVA_FILES, (clientCommitHasJavaFiles ? "true" : "false"));
        mapToAdd.put(CSV_HEADER_CLIENT_HAS_IMPORTS, (javaFileHasImports ? "true" : "false"));
        mapToAdd.put(
                CSV_HEADER_CLIENT_HAS_IMPORTS_OF_BCFILE, (javaFileHasImportsOfFileContainingBc ? "true" : "false"));
        mapToAdd.put(CSV_HEADER_CLIENT_BC_ID, bcIdentifier);
        mapToAdd.put(CSV_HEADER_BC_CATEGORY, bcCategory);
        mapToAdd.put(CSV_HEADER_CLIENT_BC_COMMIT_ID, bcCommitId);
        mapToAdd.put(CSV_HEADER_CLIENT_DIFF_CONTAINS_BC_ID, (diffContainsBcIdentifier ? "true" : "false"));
        mapToAdd.put(CSV_HEADER_CLIENT_DIFF_LINES_CONTAINS_BC_ID,
                (diffLinesContainBcIdentifier ? "true" : "false"));
        mapToAdd.put(CSV_HEADER_CLIENT_PROCESSING_TIMEOUT, (processingTimeout ? "true" : "false"));

        resultList.add(mapToAdd);
    }

    private void outputListMapToFile(long time, String clientName, List<Map<String, String>> result) {
        List<String> csvList = new ArrayList<String>();

        csvList.add(CSV_HEADER_CLIENT_NAME + ";" +
                CSV_HEADER_API_NAME + ";" +
                CSV_HEADER_CLIENT_MAIN_HAS_COMMITS + ";" +
                CSV_HEADER_CLIENT_MASTER_HAS_COMMITS + ";" +
                CSV_HEADER_API_HAS_BCS + ";" +
                CSV_HEADER_CLIENT_COMMIT_ID + ";" +
                CSV_HEADER_CLIENT_COMMIT_HAS_JAVA_FILES + ";" +
                CSV_HEADER_CLIENT_HAS_IMPORTS + ";" +
                CSV_HEADER_CLIENT_HAS_IMPORTS_OF_BCFILE + ";" +
                CSV_HEADER_CLIENT_BC_ID + ";" +
                CSV_HEADER_BC_CATEGORY + ";" +
                CSV_HEADER_CLIENT_BC_COMMIT_ID + ";" +
                CSV_HEADER_CLIENT_DIFF_CONTAINS_BC_ID + ";" +
                CSV_HEADER_CLIENT_DIFF_LINES_CONTAINS_BC_ID + ";" +
                CSV_HEADER_CLIENT_PROCESSING_TIMEOUT);

        for (Map<String, String> resultMap : result) {
            csvList.add(resultMap.get(CSV_HEADER_CLIENT_NAME) + ";" +
                    resultMap.get(CSV_HEADER_API_NAME) + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_MAIN_HAS_COMMITS) + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_MASTER_HAS_COMMITS) + ";" +
                    resultMap.get(CSV_HEADER_API_HAS_BCS) + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_COMMIT_ID) + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_COMMIT_HAS_JAVA_FILES) + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_HAS_IMPORTS) + ";" +
                    resultMap.get(
                            CSV_HEADER_CLIENT_HAS_IMPORTS_OF_BCFILE)
                    + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_BC_ID) + ";" +
                    resultMap.get(CSV_HEADER_BC_CATEGORY) + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_BC_COMMIT_ID) + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_DIFF_CONTAINS_BC_ID) + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_DIFF_LINES_CONTAINS_BC_ID) + ";" +
                    resultMap.get(CSV_HEADER_CLIENT_PROCESSING_TIMEOUT));
        }

        // Remove duplicates
        Set<String> csvSet = new LinkedHashSet<>(csvList);
        csvList.clear();
        csvList.addAll(csvSet);

        try {
            UtilFile.writeFile(UtilFile.getAbsolutePath(DIR_OUTPUT_MIGRATIONS + "/" + time + "_" + clientName + "_"
                    + ".csv"), csvList);
        } catch (Exception ex) {
            Logger.info("Migration result writefile", ex.getMessage());
        }
    }
}
