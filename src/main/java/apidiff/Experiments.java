package apidiff;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import apidiff.enums.Classifier;
import apidiff.util.UtilFile;

public class Experiments {
    public static void main(String[] args) throws Exception {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        LocalDateTime now = LocalDateTime.now();
        Experiments exp = new Experiments();

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
            Files.move(Paths.get(dirToProcess + "/" + file.getName()), Paths.get(dirProcessed + "/" + file.getName()),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void process(String formattedNow, String nameProject, String url) throws Exception {
        APIDiff diff = new APIDiff(nameProject, url);
        diff.setPath(UtilFile.getAbsolutePath("dataset"));
        Result result = diff.detectChangeAllHistory("master", Classifier.API);
        // Result result2 = diff.

        List<String> listChanges = new ArrayList<String>();
        listChanges.add(
                "Category;isBreakingChange;Description;Element;ElementType;Path;Class;RevCommit;isDeprecated;containsJavadoc");
        for (Change changeMethod : result.getChangeMethod()) {
            String change = changeMethod.getCategory().getDisplayName() + ";"
                    + changeMethod.isBreakingChange() + ";"
                    + changeMethod.getDescription() + ";"
                    + changeMethod.getElement() + ";"
                    + changeMethod.getElementType() + ";"
                    + changeMethod.getPath() + ";"
                    + changeMethod.getClass() + ";"
                    + changeMethod.getRevCommit() + ";"
                    + changeMethod.isDeprecated() + ";"
                    + changeMethod.containsJavadoc();
            listChanges.add(change);
        }
        UtilFile.writeFile("dataset/Output/output" + "_" + nameProject
                + "_" + formattedNow + ".csv", listChanges);
    }
}
