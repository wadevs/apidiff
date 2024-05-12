package apidiff;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
// import java.util.Date;
import java.util.List;
import java.util.Map;
import java.io.File;
// import java.nio.file.CopyOption;
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
    }

    private void process(String formattedNow, String nameProject, String url) throws Exception {
        APIDiff diff = new APIDiff(nameProject, url);
        diff.setPath(UtilFile.getAbsolutePath("dataset"));

        diff.detectChangeAndOuputToFiles("master", Arrays.asList(Classifier.API), nameProject);
    }
}
