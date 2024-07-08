package apidiff;

// import ch.unibe.inf.seg.gitanalyzerplus.analyze.analyzer.conflictingfile.model.files.File;
// import ch.unibe.inf.seg.gitanalyzerplus.analyze.analyzer.conflictingfile.model.files.formatter.IFileFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.time.Instant;

public class GitUtil {

    public record MergeCommit(ObjectId resolution, ObjectId base, ObjectId ours, ObjectId theirs) {
    }

    public static MergeCommit getMergeCommit(Repository repository, ObjectId resolutionID) throws IOException {
        /*
         * It turns out that, just like real children, Git doesn’t treat a merge
         * commit’s two parents equally;
         * merge commits have a “first parent” and a “second parent.”
         * The “first parent” is the branch you were already on when you typed git merge
         * (or git pull or whatever caused the merge).
         * The “second parent” is the branch that you were pulling in.
         * [https://urldefense.com/v3/__https://redfin.engineering/visualize-merge-
         * history-with-git-log-graph-first-parent-and-no-merges-c6a9b5ff109c__;!!
         * Dc8iu7o!
         * yIC4uSm7CnjvnulQBU0TZoLVm3DhRllMZ0NepJ8cUbUGi3iiK6DcxGAK99UeFoY_MBtgNFaodVpNZHe3iuvh_a2H83qGpkO9oOpcrQ$
         * ]
         *
         * => “first parent” => "ours", "second parent" => "theirs"
         */
        RevCommit resolution = getRevCommit(repository, resolutionID);
        RevCommit[] parents = resolution.getParents();
        RevCommit base = findCommonAncestor(repository, parents[0], parents[1]);
        return new MergeCommit(resolution.getId(), base.getId(), parents[0].getId(), parents[1].getId());
    }

    public static MergeCommit getMergeCommit(Repository repository, ObjectId resolutionID, ObjectId baseID)
            throws IOException {
        /*
         * It turns out that, just like real children, Git doesn’t treat a merge
         * commit’s two parents equally;
         * merge commits have a “first parent” and a “second parent.”
         * The “first parent” is the branch you were already on when you typed git merge
         * (or git pull or whatever caused the merge).
         * The “second parent” is the branch that you were pulling in.
         * [https://urldefense.com/v3/__https://redfin.engineering/visualize-merge-
         * history-with-git-log-graph-first-parent-and-no-merges-c6a9b5ff109c__;!!
         * Dc8iu7o!
         * yIC4uSm7CnjvnulQBU0TZoLVm3DhRllMZ0NepJ8cUbUGi3iiK6DcxGAK99UeFoY_MBtgNFaodVpNZHe3iuvh_a2H83qGpkO9oOpcrQ$
         * ]
         *
         * => “first parent” => "ours", "second parent" => "theirs"
         */
        RevCommit resolution = getRevCommit(repository, resolutionID);
        RevCommit[] parents = resolution.getParents();
        RevCommit base = getRevCommit(repository, baseID);
        return new MergeCommit(resolution.getId(), base.getId(), parents[0].getId(), parents[1].getId());
    }

    // /**
    // * Retrieve the file specified by the file name that was committed.
    // *
    // * @param revCommit the commit
    // * @param fileName the name of the file to be retrieved from the commit
    // * @return the file included in the commit
    // * @throws IOException if the file could not be retrieved from the Git history
    // */
    // public static File getFile(Repository repository, RevCommit revCommit, String
    // fileName, IFileFormatter fileFormatter) throws IOException {
    // TreeWalk treeWalk = TreeWalk.forPath(
    // repository,
    // fileName,
    // revCommit.getTree()
    // );
    // if (treeWalk == null) {
    // return new File(new byte[0], fileFormatter);
    // } else {
    // ObjectId blobId = treeWalk.getObjectId(0);
    // ObjectReader objectReader = repository.newObjectReader();
    // ObjectLoader objectLoader = objectReader.open(blobId);
    // objectReader.close();
    // return new File(objectLoader.getBytes(), fileFormatter);
    // }
    // }

    public static RevCommit findCommonAncestor(Repository repository, ObjectId commit1, ObjectId commit2)
            throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            // NOTE: RevCommits need to be produced by the same RevWalk instance otherwise
            // they can't be compared.
            RevCommit revCommit1 = revWalk.parseCommit(commit1);
            RevCommit revCommit2 = revWalk.parseCommit(commit2);

            revWalk.setRevFilter(RevFilter.MERGE_BASE);
            revWalk.markStart(revCommit1);
            revWalk.markStart(revCommit2);
            return revWalk.next();
        }
    }

    public static boolean isMergeCommit(RevCommit commit) {
        // A merge commit has more than one parent
        return commit.getParentCount() > 1;
    }

    public static Instant getCommitDate(RevCommit commit) {
        return Instant.ofEpochSecond(commit.getCommitTime()); // UTC
    }

    public static RevCommit getRevCommit(Repository repository, String commitHash) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            return revWalk.parseCommit(repository.resolve(commitHash));
        }
    }

    public static RevCommit getRevCommit(Repository repository, ObjectId objectId) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            return revWalk.parseCommit(objectId);
        }
    }
}
