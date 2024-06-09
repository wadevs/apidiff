package apidiff;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
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

import com.google.common.collect.MapConstraint;
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

public class Experiments {
    public static void main(String[] args) throws Exception {
        // String mode = "process diff"; // process diff
        // String mode = "test/get file names"; // tests -> ok
        // String mode = "test/get file content"; // tests -> ok
        // String mode = "test/get diffs"; // tests -> ok
        String mode = "test migration"; // tests ->

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

            for (Map<String, String> map : csvMapList) {
                String[] githubLinks = map.get("GithubLinks").split(",");
                String nameProject = map.get("ArtifactTitle");
                try {
                    System.out.println(githubLinks[0].trim());
                    exp.process(dtf.format(now), nameProject, githubLinks[0].trim() + ".git");
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }

            for (File file : fileList) {
                Files.move(Paths.get(dirToProcess + "/" + file.getName()),
                        Paths.get(dirProcessed + "/" + file.getName()),
                        StandardCopyOption.REPLACE_EXISTING);
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
            exp.migrationIdentifier("https://github.com/wadevs/DummyMaven", "DummyMaven", "MPAndroidChart");
        }
    }

    private void process(String formattedNow, String nameProject, String url) throws Exception {
        APIDiff diff = new APIDiff(nameProject, url);
        diff.setPath(UtilFile.getAbsolutePath("dataset"));

        diff.detectChangeAndOuputToFiles("master", Arrays.asList(Classifier.API), nameProject);
    }

    private void migrationIdentifier(String clientGitUrl, String projectName, String apiName) {
        // String clientGitUrl = "";

        try {
            GitService service = new GitServiceImpl();
            Repository repository = service.openRepositoryAndCloneIfNotExists(
                    UtilFile.getAbsolutePath("dataset"), projectName, clientGitUrl);
            RevWalk revWalk = service.createAllRevsWalk(repository, "main");

            List<Change> BCs = new ArrayList<Change>();
            // TODO: parse BC files into List of Change or ChangeLike object
            String bcsDir = UtilFile.getAbsolutePath("dataset/Output/");
            File[] fileList = new File(bcsDir).listFiles();

            List<Map<String, String>> bcsMapList = new ArrayList<>();
            for (File file : fileList) {
                if (file.isFile() && file.getName().contains(apiName)) {
                    bcsMapList.addAll(UtilFile.convertCSVFileToListofMaps(file.getAbsolutePath()));
                }
            }

            Iterator<RevCommit> i = revWalk.iterator();
            while (i.hasNext()) {
                RevCommit currentClientCommit = i.next();
                // Granularity: commit time
                int clientCommitTime = currentClientCommit.getCommitTime();

                // TODO: Probably not needed anymore
                List<String> javaFileNamesInCommit = getJavaFileNamesInCommit(repository, currentClientCommit); // get
                                                                                                                // files
                                                                                                                // in
                                                                                                                // commit

                List<Pair<String, String>> diffsForFilesInCommit = getAllDiffsWithParentFromCommit(repository,
                        currentClientCommit);

                List<Pair<String, String>> javaFileNamesAndContentInCommit = getJavaFileContentInCommitWithTreeWalk(
                        repository, currentClientCommit);

                for (Pair<String, String> javaFileNameAndContent : javaFileNamesAndContentInCommit) {
                    List<Map<String, String>> BcTimeFiltered = new ArrayList<Map<String, String>>();
                    // for (Change change : BCs) {
                    for (Map<String, String> bcMap : bcsMapList) {
                        String[] revCommit = bcMap.get("RevCommit").split(" ");

                        try {
                            int changeCommitTime = Integer.parseInt(revCommit[2]);
                            if (changeCommitTime < clientCommitTime) {
                                BcTimeFiltered.add(bcMap);
                            }
                        } catch (Exception e) {
                            // Ignore invalid data
                        }

                    }

                    String codeContent = javaFileNameAndContent.getValue();

                    List<Map<String, String>> importBCs = new ArrayList<Map<String, String>>();
                    List<String> imports = ImportMatching.getImports(codeContent);
                    for (String importStr : imports) {
                        for (Map<String, String> bcMap : BcTimeFiltered) {
                            String path = bcMap.get("Path");
                            if (path.contains(importStr)) {
                                importBCs.add(bcMap);
                            }
                        }
                    }

                    String diffsForFile = "";
                    for (Pair<String, String> diffs : diffsForFilesInCommit) {
                        if (diffs.getKey().equals(javaFileNameAndContent.getKey())) {
                            diffsForFile = diffs.getValue();
                            break;
                        }
                    }
                    for (Map<String, String> bcMap : BcTimeFiltered) {
                        if (diffsForFile.contains(bcMap.get("Element"))) {
                            // log migration
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Logger.info(projectName, ex.getMessage());
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
}
