package apidiff;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
    public static final int API_NAME_POSITION = 4;
    public static final int API_OUTPUT_TIME_POSITION = 0;
    public static final int BC_REV_COMMIT_POSITION = 2;
    public static final int FILE_NAME_API_NAME_POSITION = 1;

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

            List<Map<String, String>> csvMapList = new ArrayList<>();
            for (File file : fileList) {
                if (file.isFile()) {
                    csvMapList.addAll(UtilFile.convertCSVFileToListofMaps(file.getAbsolutePath()));
                }
            }

            // Regroup all the artifacts that share a common git repository
            Map<String, String> gitLinkSharedMap = new HashMap<String, String>();
            for (Map<String, String> map : csvMapList) {
                String[] githubLinks = map.get("GithubLinks").split(",");
                String nameProject = map.get("ArtifactTitle");
                String firstLink;

                try {
                    firstLink = githubLinks[0].trim();
                    String currentProjectNamesForLink = gitLinkSharedMap.get(firstLink);

                    // Append the current name if there are other artifacts already, use $ as a
                    // filename compatible separator
                    if (currentProjectNamesForLink.length() > 0) {
                        gitLinkSharedMap.put(firstLink, currentProjectNamesForLink + "$" + nameProject);
                    }
                    // Put the first one in otherwise
                    else {
                        gitLinkSharedMap.put(firstLink, nameProject);
                    }
                } catch (Exception ex) {
                    Logger.info(githubLinks, ex.getMessage());
                }
            }

            for (Map<String, String> map : csvMapList) {
                String[] githubLinks = map.get("GithubLinks").split(",");
                String nameProject = map.get("ArtifactTitle");
                try {
                    String firstLink = githubLinks[0].trim();
                    System.out.println(firstLink);
                    // exp.process(dtf.format(now), nameProject, githubLinks[0].trim() + ".git");
                    // Use the agregated name as project name
                    exp.process(dtf.format(now), gitLinkSharedMap.get(firstLink), firstLink + ".git");
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
            String dirToProcessUsages = UtilFile.getAbsolutePath("dataset/ToProcess/Usages");
            // String dirProcessed = UtilFile.getAbsolutePath("dataset/Processed/");
            File[] fileList = new File(dirToProcessUsages).listFiles();

            for (File usagesFile : fileList) {
                if (usagesFile.isFile()) {
                    // apiName is the 5th element in the filename by construction
                    String apiName = usagesFile.getName().split("_")[API_NAME_POSITION];
                    // apiOutputTime is the 1st element in the filename by construction
                    String apiOutputTime = usagesFile.getName().split("_")[API_OUTPUT_TIME_POSITION];

                    File outputApiDir = new File("dataset/Output/" + apiName);
                    if (!outputApiDir.exists()) {
                        outputApiDir.mkdirs();
                    }

                    List<Map<String, String>> usagesCsvMapList = new ArrayList<>();
                    usagesCsvMapList.addAll(UtilFile.convertCSVFileToListofMaps(usagesFile.getAbsolutePath()));

                    for (Map<String, String> usageCsvMap : usagesCsvMapList) {
                        // Should only have one link
                        String githubLink = usageCsvMap.get("GithubLinks").trim().split(" ")[0];
                        String projectName = usageCsvMap.get("ArtifactTitle");

                        File outputApiClientDir = new File(outputApiDir + "/" + projectName);
                        if (!outputApiClientDir.exists()) {
                            outputApiClientDir.mkdirs();
                        }

                        exp.migrationIdentifier(githubLink, projectName, apiName, apiOutputTime, outputApiClientDir);
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

        try {
            GitService service = new GitServiceImpl();
            Repository repository = service.openRepositoryAndCloneIfNotExists(
                    UtilFile.getAbsolutePath("dataset"), projectName, clientGitUrl);
            RevWalk revWalk = service.createAllRevsWalk(repository, "main");

            int nbCommitsInBranch = nbCommitsInBranch(service, repository, "main");
            if (nbCommitsInBranch <= 0) {
                revWalk = service.createAllRevsWalk(repository, "master");
                nbCommitsInBranch = nbCommitsInBranch(service, repository, "master");
            }

            // restrict to < 5000 commits, YEET this restriction probably
            if (nbCommitsInBranch > 0 && nbCommitsInBranch < 5000) {
                List<Change> BCs = new ArrayList<Change>();

                String bcsDir = UtilFile.getAbsolutePath("dataset/Output/");
                File[] fileList = new File(bcsDir).listFiles();

                List<Map<String, String>> bcsMapList = new ArrayList<>();
                for (File file : fileList) {
                    // if (file.isFile() && file.getName().contains(apiName)) {
                    if (file.isFile() && file.getName().split("_")[FILE_NAME_API_NAME_POSITION].equals(apiName)) {
                        bcsMapList.addAll(UtilFile.convertCSVFileToListofMaps(file.getAbsolutePath()));
                    }
                }

                int clientCommitCounter = 0;

                Iterator<RevCommit> i = revWalk.iterator();
                while (i.hasNext()) {
                    RevCommit currentClientCommit = i.next();
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

                    // Test: remove time check filter
                    for (Pair<String, String> javaFileNameAndContent : javaFileNamesAndContentInCommit) {
                        List<Map<String, String>> BcTimeFiltered = new ArrayList<Map<String, String>>();
                        // for (Change change : BCs) {
                        // TODO: Move time filtering out of loop ?
                        for (Map<String, String> bcMap : bcsMapList) {
                            String[] revCommit = bcMap.get("RevCommit").split(" ");

                            try {
                                int changeCommitTime = Integer.parseInt(revCommit[BC_REV_COMMIT_POSITION]);
                                if (changeCommitTime < clientCommitTime) {
                                    BcTimeFiltered.add(bcMap);
                                }
                            } catch (Exception e) {
                                // Ignore invalid data
                            }

                        }

                        String codeContent = javaFileNameAndContent.getValue();

                        // if E import -> log as migration ?
                        // if nothing -> probably issue in identifier
                        List<Map<String, String>> importBCs = new ArrayList<Map<String, String>>();
                        List<String> imports = ImportMatching.getImports(codeContent);
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

                        boolean logImports = false;
                        if (logImports) {// log import matches
                            try {
                                if (matchedImports.size() > 0) {
                                    UtilFile.writeFile(
                                            migrationLogOutputDir + "/" + "matchedimports_" + apiName + "_"
                                                    + projectName
                                                    + "_"
                                                    + potentialMigrationCounter + ".txt",
                                            matchedImports);
                                    potentialMigrationCounter++;
                                }
                            } catch (Exception ex) {
                                try {
                                    Logger.info(projectName, ex.getMessage());
                                } catch (IllegalStateException e) {
                                    // Ignore (comes from java 12+)
                                }
                            }
                        }

                        String diffsForFile = "";
                        for (Pair<String, String> diffs : diffsForFilesInCommit) {
                            if (diffs.getKey().equals(javaFileNameAndContent.getKey())) {
                                diffsForFile = diffs.getValue();
                                // TODO: check influence of break here,may need append if more that one diffs
                                // per file
                                break;
                            }
                        }

                        // Remove this restriction for testing
                        boolean detectActualMigrationCandidate = true;
                        if (detectActualMigrationCandidate) {
                            if (diffsForFile.length() > 0) {

                                for (Map<String, String> bcMap : importBCs) {
                                    try {
                                        TypeDeclaration typeDec = JavaParserUtility.parseExpression(
                                                // "public class X {" + bcMap.get("Element") + "{}}", false);
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
                                        // element = ((MethodDeclaration) typeDec.bodyDeclarations().getFirst())
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
                                                element = ((MethodDeclaration) typeDec.bodyDeclarations().getFirst())
                                                        .getName()
                                                        .toString();
                                            } else if (dec instanceof FieldDeclaration) {
                                                element = ((FieldDeclaration) typeDec.bodyDeclarations().getFirst())
                                                        .fragments()
                                                        .getFirst()
                                                        .toString();
                                            } else if (dec instanceof TypeDeclaration) {
                                                element = ((TypeDeclaration) typeDec.bodyDeclarations().getFirst())
                                                        .getName()
                                                        .toString();
                                            }
                                        }

                                        // List<Token> tokenList = JavaParserUtility
                                        // // .tokensToAST("public class X {" + bcMap.get("Element") + "{}}", cUnit);
                                        // .tokensToAST(bcMap.get("Element") + ";", cUnit);
                                        if (diffsForFile.contains(element)) {
                                            // System.out.println("match");
                                            // log migration
                                            List<String> diffsAsList = new ArrayList<String>();
                                            diffsAsList.add(diffsForFile);

                                            try {
                                                UtilFile.writeFile(
                                                        migrationLogOutputDir + "/" + apiName + "_" + projectName + "_"
                                                                + potentialMigrationCounter + ".txt",
                                                        diffsAsList);
                                                potentialMigrationCounter++;
                                            } catch (Exception ex) {
                                                try {
                                                    Logger.info(projectName, ex.getMessage());
                                                } catch (IllegalStateException e) {
                                                    // Ignore (comes from java 12+)
                                                }
                                            }
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
                        }
                    }
                    clientCommitCounter++;
                    // Temporary (?) restriction to avoid "endless" processing of huge projects
                    if (clientCommitCounter > APIDiff.COMMITS_TO_PROCESS_LIMIT) {
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            try {
                Logger.info(projectName, ex.getMessage());
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
}
