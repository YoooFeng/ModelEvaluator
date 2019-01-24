package com.iscas.yf;

import com.iscas.yf.entity.BuildPredict;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.*;
import java.util.*;

public class GitHubRepoService {

    // log4j - 日志输出
    private static Logger logger = Logger.getLogger(GitHubRepoService.class);

    // 仓库地址
    private static String WIN_GIT_DIR = "C:\\yang\\workplace\\TravisRepo\\";

    // 新建本地仓库
    public static Repository createNewRepository(String localRepoPath) throws IOException {
        // 将原目录删除
        File localPath = File.createTempFile(localRepoPath, "");
        if(!localPath.delete()) {
            throw new IOException("Could not delete temporary file " + localPath);
        }

        // 新建目录
        Repository repository = FileRepositoryBuilder.create(new File(localPath, ".git"));
        repository.create();

        return repository;
    }

    /**
     * Clone Git仓库到本地目录
     * @Param remoteUrl - Github仓库地址
     * @Param repoDir - 本地代码托管地址
     * */
    public static void gitClone(String remoteUrl, File repoDir) {
        try {
            Git git = Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(repoDir)
                    .call();

            logger.info("Cloning from " + remoteUrl + " to " + git.getRepository());
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    /**
     * checkOut进行分支切换
     * @Param repoDir - 本地仓库地址，在该目录下需要.git文件
     * @Param version - 要切换的分支名字
     * */
    public static void gitCheckout(File repoDir, String version) {
        File RepoGitDir = new File(repoDir.getAbsolutePath() + "/.git");
        if (!RepoGitDir.exists()) {
            logger.info("Error! Not Exists : " + RepoGitDir.getAbsolutePath());
        } else {
            Repository repo = null;
            try {
                repo = new FileRepository(RepoGitDir.getAbsolutePath());
                Git git = new Git(repo);
                CheckoutCommand checkout = git.checkout();
                checkout.setName(version);
                checkout.call();
                logger.info("Checkout to " + version);

                PullCommand pullCmd = git.pull();
                pullCmd.call();

                logger.info("Pulled from remote repository to local repository at " + repo.getDirectory());
            } catch (Exception e) {
                logger.info(e.getMessage() + " : " + RepoGitDir.getAbsolutePath());
            } finally {
                if (repo != null) {
                    repo.close();
                }
            }
        }
    }

    /**
     * 将代码pull下来
     * @Param repoDir - 仓库地址，同样需要在当前目录下存在.git文件
     * */
    public static void gitPull(File repoDir) {
        File RepoGitDir = new File(repoDir.getAbsolutePath() + "/.git");
        if (!RepoGitDir.exists()) {
            logger.error("Error! Not Exists : " + RepoGitDir.getAbsolutePath());
        } else {
            Repository repo = null;
            try {
                repo = new FileRepository(RepoGitDir.getAbsolutePath());
                Git git = new Git(repo);
                PullCommand pullCmd = git.pull();
                pullCmd.call();

                logger.info("Pulled from remote repository to local repository at " + repo.getDirectory());
            } catch (Exception e) {
                logger.error(e.getMessage() + " : " + RepoGitDir.getAbsolutePath());
            } finally {
                if (repo != null) {
                    repo.close();
                }
            }
        }
    }

    /**
     * 从远程代码库fetch更新
     * @Param repoDir - 仓库地址，同样需要在当前目录下存在.git文件
     * */
    public static void gitFetch(Git git){
        try{
            FetchCommand fetchCmd = git.fetch();
            fetchCmd.call();
            logger.info("Fetched from remote repository to local repository.");
        } catch (Exception e){
            logger.error(e.getMessage());
        }

    }

    /**
     * 打印当前仓库的状态
     * @Param repoDir - 仓库地址，同样需要在当前目录下存在.git文件
     * */
    public static void gitShowStatus(File repoDir) {
        File RepoGitDir = new File(repoDir.getAbsolutePath() + "/.git");
        if (!RepoGitDir.exists()) {
            logger.info("Error! Not Exists : " + RepoGitDir.getAbsolutePath());
        } else {
            Repository repo = null;
            try {
                repo = new FileRepository(RepoGitDir.getAbsolutePath());
                Git    git    = new Git(repo);
                Status status = git.status().call();
                logger.info("Git Change: " + status.getChanged());
                logger.info("Git Modified: " + status.getModified());
                logger.info("Git UncommittedChanges: " + status.getUncommittedChanges());
                logger.info("Git Untracked: " + status.getUntracked());
            } catch (Exception e) {
                logger.info(e.getMessage() + " : " + repoDir.getAbsolutePath());
            } finally {
                if (repo != null) {
                    repo.close();
                }
            }
        }
    }

    /**
     * 通过输入新、旧commit的commitId，列出两次commit的更改和不同。
     * @Param repository - Git仓库对象
     * @Param git - 与Repo绑定的Git对象
     * @Param oldCommit - old commitId
     * @Param newCommit - new commitId
     * */
    public static List<String> listFileDiffs(Repository repository, Git git, String oldCommit, String newCommit) throws GitAPIException, IOException {
        final List<DiffEntry> diffs = git.diff()
                .setOldTree(prepareTreeParser(repository, oldCommit))
                .setNewTree(prepareTreeParser(repository, newCommit))
                .call();
        List<String> res = new ArrayList<>();
        System.out.println("Found: " + diffs.size() + " differences");
        for (DiffEntry diff : diffs) {
            res.add(diff.getChangeType() + ": " +
                    (diff.getOldPath().equals(diff.getNewPath()) ? diff.getNewPath() : diff.getOldPath() + " -> " + diff.getNewPath()));
        }
        return res;
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
        // from the commit we can build the tree which allows us to construct the TreeParser
        // noinspection Duplicates
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();

            return treeParser;
        }
    }

    /**
     * @Function 根据commitID获取更改的文件的代码行数以及详细信息.
     *           原理是将两次build之间最早的一次commit跟HEAD所指向的最新仓库进行比较, 因此要求HEAD不被修改
     * @Param repo - 代码仓库
     * @Param commitHash - 目标commit的hash标识符
     * */
    public static ArrayList<String> getModifiedLines(Repository repo, int commits) throws GitAPIException{

        String pz = "";
        while(commits-- > 0){
            pz += "^";
        }

        Git git = new Git(repo);

        ObjectId old = null;

        ObjectId head = null;

        // A new reader to read objects from getObjectDatabase()
        ObjectReader reader = repo.newObjectReader();

        // Create a new parser.
        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        List<DiffEntry> diffs = null;

        try {
            // Parse a git repository string and return an ObjectId
            // In fact, we only need to pass the oldest commit and compare it with current HEAD
            old = repo.resolve("HEAD" + pz + "^{tree}");

            // Could replace it with a specific commitID
            head = repo.resolve("HEAD^{tree}");

            // Reset this parser to walk through the given tree
            oldTreeIter.reset(reader, old);
            newTreeIter.reset(reader, head);

            diffs = git.diff()// Returns a command object to execute a diff command
                    .setNewTree(newTreeIter)
                    .setOldTree(oldTreeIter)
                    .call();// Returns a DiffEntry for each path which is different

        } catch (RevisionSyntaxException | IOException | GitAPIException e) {
            e.printStackTrace();
        }

        // DiffLineCountFilter d = new DiffLineCountFilter();
        // out is the stream the formatter will write to
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        //Create a new formatter with a default level of context.
        DiffFormatter df = new DiffFormatter(out);

        // Set the repository the formatter can load object contents from.
        df.setRepository(repo);
        ArrayList<String> diffText = new ArrayList<>();
        // A DiffEntry is 'A value class representing a change to a file' therefore for each file you have a diff entry
        for(DiffEntry diff : diffs)
        {
            try {
                // Only contains modified lines of code, excludes unchanged lines.
                df.setContext(0);

                // Format a patch script for one file entry.
                // Format: @@ -4,4 +4,4 @@ == @@ -<startLineNumber>, <width> +<startLineNumber>, <width> @@
                df.format(diff);
                RawText r = new RawText(out.toByteArray());
                r.getLineDelimiter();


                diffText.add(out.toString());
                out.reset();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return diffText;
    }

    @Deprecated
    public static int listLinesOfCode(Repository repo, String fileName, String commitHash){
        try {
            BlameCommand blamer = new BlameCommand(repo);

            // Assign one particular commit
            // ObjectId commitID = repo.resolve(commitHash + "^{tree}");
            ObjectId commitID = repo.resolve("HEAD~~");

            blamer.setStartCommit(commitID);
            blamer.setFilePath(fileName);
            BlameResult blame = blamer.call();

            // read the number of lines from the given revision, this excludes changes from the last two commits due to the "~~" above
            int lines = countLinesOfFileInCommit(repo, commitID, fileName);
            for (int i = 0; i < lines; i++) {
                RevCommit commit = blame.getSourceCommit(i);
                System.out.println("Line: " + i + ": " + commit);
            }

            final int currentLines;
            try (final FileInputStream input = new FileInputStream(fileName)) {
                currentLines = IOUtils.readLines(input, "UTF-8").size();
            }
            System.out.println("Displayed commits responsible for " + lines + " lines of " + fileName + ", current version has " + currentLines + " lines");
            return lines;
        } catch (IOException | GitAPIException e){
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 首先将远程仓库的变化fetch下来, 然后对比本地仓库和远程仓库之间的差异.
     * 这样可以略过Jenkins提供的CommitSets机制, 直接利用Hook执行构建.
     * @Param Git对象
     * @return 所有统计的结果, 包括文件内容 类型 作者 路径
     * */
    public static Map<String, String> compareLocalAndRemote(Git git) throws Exception{

        Repository repo = git.getRepository();

        // 将变化fetch下来, 同时记录下fetch的所有内容
        FetchResult res = git.fetch().setCheckFetchedObjects(true).call();

        // Fetch到本地后, 已经Fetch的内容不会显示在res中. 需要显示commitId来寻找author?
        System.out.println(res.getMessages());

        ObjectId fetchHead = repo.resolve("FETCH_HEAD^{tree}");

        ObjectId localHead = repo.resolve("HEAD^{tree}");

        // RevWalk walk = new RevWalk(repo);

        // A new reader to read objects from getObjectDatabase()
        ObjectReader reader = repo.newObjectReader();

        // Create a new parser.
        CanonicalTreeParser localTreeIter = new CanonicalTreeParser();
        CanonicalTreeParser fetchTreeIter = new CanonicalTreeParser();

        localTreeIter.reset(reader, localHead);
        fetchTreeIter.reset(reader, fetchHead);

        List<DiffEntry> diffs = null;

        diffs = git.diff()
                .setNewTree(fetchTreeIter)
                .setOldTree(localTreeIter)
                .call();

        // 不使用filter而使用Range, 获取本地和Fetch之间的所有Commit hash
        ObjectId localCommit = repo.exactRef("HEAD").getObjectId();
        ObjectId fetchCommit = repo.exactRef("FETCH_HEAD").getObjectId();
        String latestCommit = fetchCommit.getName();

        Iterable<RevCommit> commits = git.log().addRange(localCommit ,fetchCommit).call();

        // 然后从commitId中获取提交commit的作者邮箱地址, 空格分隔
        StringBuffer authors = new StringBuffer();
        // 修改文件的路径, 空格分隔
        StringBuffer editedPaths = new StringBuffer();

        for(RevCommit commit : commits){
            PersonIdent authorIdent = commit.getAuthorIdent();

            // committer的邮箱地址, 空格分隔
            String authorEmail = authorIdent.getEmailAddress();
            authors.append(authorEmail + " ");

            // 用户名
            // String authorName = authorIdent.getName();
            // committer的邮箱地址
            // String authorEmail = authorIdent.getEmailAddress();
            // 提交的时间
            // Long when = authorIdent.getWhen().getTime();
        }


        ArrayList<String> diffText = new ArrayList<>();

        // out is the stream the formatter will write to
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Create a new formatter with a default level of context.
        DiffFormatter df = new DiffFormatter(out);

        // 这个语句是什么作用?
        df.setDiffComparator(RawTextComparator.DEFAULT.WS_IGNORE_ALL);
        df.setRepository(repo);

        Map<String, String> analysis = new HashMap<>();

        // +
        int addSize = 0;
        // -
        int subSize = 0;

        for(DiffEntry diff : diffs){
            // Only contains modified lines of code, excludes unchanged lines.
            // df.setContext(0);

            // 获得修改文件的完整路径 格式: (repoName/不显示) [src/main/java/works/weave/socks/shipping/xxx.java]
            String filePath = diff.getOldPath();
            // 去掉最后一个斜杠后的内容,保留斜杠/. [src/main/java/works/weave/socks/shipping/]
            filePath = filePath.substring(0, filePath.lastIndexOf("/") + 1);

            // TODO: 如何优化一下记录的路径, 去除重复的路径.
            editedPaths.append(filePath + " ");

            // Format: @@ -4,4 +4,4 @@ == @@ -<startLineNumber>, <width> +<startLineNumber>, <width> @@
            df.format(diff);

            FileHeader fileHeader = df.toFileHeader(diff);

            List<HunkHeader> hunks = (List<HunkHeader>) fileHeader.getHunks();
            for(HunkHeader hunkHeader : hunks){
                EditList editList = hunkHeader.toEditList();
                for(Edit edit : editList){
                    subSize += edit.getEndA() - edit.getBeginA();
                    addSize += edit.getEndB() - edit.getBeginB();
                }
            }

            RawText r = new RawText(out.toByteArray());
            r.getLineDelimiter();

            diffText.add(out.toString());
            out.reset();
        }

        String modifiedTypes = getModifiedFileTypes(diffText);

        // 增加的行数和减少的行数统一计算, 之后可以有更细粒度的处理方法. 统计行数测试没问题
        analysis.put("ModifiedLines", String.valueOf(subSize + addSize));
        // 被修改的文件类型.
        analysis.put("ModifiedTypes", modifiedTypes);
        // 两次构建之间所有commit提交者的邮箱
        analysis.put("Authors", authors.toString());
        // 将Fetch Head的commit Id 存到Map中, 作为最新一次commitId跟build绑定
        analysis.put("LatestCommit", latestCommit);
        // 修改的文件路径
        analysis.put("ModifiedPaths", editedPaths.toString());

        return analysis;
    }

    /**
     * @Function count modified lines of code in a particular commit
     * @Param repository - A Git repo
     * @Param ObjectId - commitId
     * @Param name - Particular file which has been modified
     * */
    private static int countLinesOfFileInCommit(Repository repository, ObjectId commitID, String name) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitID);
            RevTree tree = commit.getTree();
            System.out.println("Having tree: " + tree);

            // now try to find a specific file
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(name));
                if (!treeWalk.next()) {
                    throw new IllegalStateException("Did not find expected file: " + name);
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);

                // load the content of the file into a stream
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                loader.copyTo(stream);

                revWalk.dispose();

                return IOUtils.readLines(new ByteArrayInputStream(stream.toByteArray()), "UTF-8").size();
            }
        }
    }


    /**
     * @Function 获取所有修改的文件类型
     * @Param
     * */
    public static String getModifiedFileTypes(ArrayList<String> changeSet) throws Exception{
        StringBuffer sb = new StringBuffer();
        if(changeSet == null || changeSet.size() == 0) return "";
        for (String s : changeSet) {
            int idx1 = s.indexOf("a/");
            int idx2 = s.indexOf("b/");
            // 后缀名切割开. 特殊情况 -> .travis.yml & Dockerfile
            String s1 = s.substring(idx1, idx2).trim();

            // 如果不存在, 直接加入a/ 文件名
            if(!sb.toString().contains(s1)) sb.append(s1);
            // String[] s1 = s.substring(idx1, idx2).trim().split("\\.");
            // for (String s2 : s1) {
            //     // s2是后缀名
            //     s2 = s1[1].trim();
            //     // 如果已经存在了, 就不往里面加入重复的后缀名
            //     if(sb.toString().contains(s2)) continue;
            //     // 加入没有后缀名的一些文件
            //     else if(s2.equals("Dockerfile")) sb.append(s2 + ", ");
            //     else sb.append("*." + s2 + ", ");
            // }
        }
        return sb.toString();
    }


    // public static String judgeFileType(String fileTypes){
    //     StringBuffer sb = new StringBuffer();
    //
    //     // 是否包含核心代码
    //     if(fileTypes.contains("*.java") || fileTypes.contains("*.cpp")
    //             || fileTypes.contains("*.py")|| fileTypes.contains("*.java")){
    //         sb.append("CORE");
    //     }
    //     // 这里的test不是文件后缀名, 而是测试代码的包名
    //     if(fileTypes.contains("test")){
    //         sb.append("TEST");
    //     }
    //     if(fileTypes.contains("Dockerfile")){
    //         sb.append("DOCKERFILE");
    //     }
    //     // 是否修改了文档
    //     if(fileTypes.contains("*.md") || fileTypes.contains("*.txt") ||
    //             fileTypes.contains("*.doc") || fileTypes.contains("*.ppt") ||
    //             fileTypes.contains("*.docx") || fileTypes.contains("*.yml") ||
    //             fileTypes.contains("*.xml")){
    //         sb.append("DOCUMENT");
    //     }
    //     return sb.toString();
    // }

    /**
     * 本地回溯指定数量个commits, 得到作者列表. 注意是从最新的commit向前回溯
     * @Param commitCount - 指定要追溯的commit个数
     * @Param Git
     * @return
     * */
    public static StringBuffer getExperiencedAuthors(int commitCount, Git git) throws Exception{

        Iterable<RevCommit> commits = git.log().setMaxCount(commitCount).call();
        // 然后从commitId中获取提交commit的作者
        StringBuffer experiencedAuthors = new StringBuffer();

        for(RevCommit commit : commits){
            PersonIdent authorIdent = commit.getAuthorIdent();

            // committer的GitHub用户名
            // String authorName = authorIdent.getName();

            String authorEmail = authorIdent.getEmailAddress();
            experiencedAuthors.append(authorEmail + " ");

            // committer的邮箱地址
            // String authorEmail = authorIdent.getEmailAddress();
            // 提交的时间
            // Long when = authorIdent.getWhen().getTime();
        }

        return experiencedAuthors;
    }

    /**
     * 实验用，将指定的commitHash对应的仓库修改特征提取出来。
     * @Param gitDir - .git文件夹所在的目录
     * @Param commitHash - 对应commit的hash值
     * @return BuildPredict
     *
     * */
    public static BuildPredict getPredictByCommit(String projectName, String commitHash) throws Exception{

        String projectDir = WIN_GIT_DIR + projectName + "\\";
        File gitDirFile = new File(projectDir);
        if(!gitDirFile.exists()) {
            gitDirFile.mkdir();
        }

        // 本地仓库不存在就从远程仓库pull代码
        if(!new File(projectDir + "\\.git").exists()) {
            String remoteUrl = "https://github.com/" + projectName.replace("%2F","/") + ".git";
            gitClone(remoteUrl, gitDirFile);
        }

        BuildPredict bp = new BuildPredict();
        bp.setCommitHash(commitHash);

        // 文件修改的情况
        int linesAdded = 0;
        int linesDeleted = 0;
        // int filesChanged = 0;
        int total = 0;
        StringBuffer authors = new StringBuffer();

        // 通过文件路径得到Git对象
        Git git = Git.open(new File(projectDir + "\\.git"));

        // 保持仓库最新状态
//        git.pull().call();

        Repository repo = git.getRepository();
        RevWalk rw = new RevWalk(repo);

        try {
            // 将sha1转化为ObjectId再解析
            ObjectId commitId = ObjectId.fromString(commitHash);
            RevCommit commit = rw.parseCommit(commitId);

            if(commit.getParents() != null && commit.getParents().length > 0) {

            }
            RevCommit parentCommit = rw.parseCommit(commit.getParent(0).getId());

            // 当前commit的Author date时间作为基准
            Date baseline = commit.getAuthorIdent().getWhen();

            // 本次commit的作者
            int authorCount = 1;
            authors.append(commit.getAuthorIdent().getEmailAddress());

            // 记录当前commit的author
            bp.setCommitAuthor(authors.toString());

            // 获得当前commit的所有parent commit对象
            RevCommit[] parents = commit.getParents();

            // 优化了特征值，只考察当前commit的parentcommit，过于久远的commit对当前影响不大
            // 遍历这些parent，直到时间间隔超过三个月(90天)
            for(RevCommit p : parents) {
                if(p.getTree() == null) {
                    continue;
                }
                if( ((baseline.getTime() - p.getAuthorIdent().getWhen().getTime()) / (1000 * 60 * 60 * 24)) <= 90) {
                    // 如果不包含(即不是一个开发者)就计数
                    if(!authors.toString().contains(p.getAuthorIdent().getEmailAddress())) {
                        authorCount += 1;
                        authors.append("|" + p.getAuthorIdent().getEmailAddress());
                    }
                } else {
                    // 超过三个月，直接退出循环
                    break;
                }
            }

            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
            df.setRepository(repo);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);

            List<DiffEntry> diffs;
            diffs = df.scan(parentCommit.getTree(), commit.getTree());

            // 修改的文件总数量
            int filesChanged = diffs.size();
            bp.setFileMofified(filesChanged);

            for (DiffEntry diff : diffs) {
                for (Edit edit : df.toFileHeader(diff).toEditList()) {
                    linesDeleted += edit.getEndA() - edit.getBeginA();
                    linesAdded += edit.getEndB() - edit.getBeginB();
                }
            }

            // 将commit hash对应的特征值提取出来，主要是loc、teamsize(本次commit涉及的修改者人数)
            total = linesAdded + linesDeleted;

            // 修改代码总数
            bp.setLineOfCodes(total);
            // 本次commit涉及的修改者人数
            bp.setTeamSize(authorCount);

            return bp;
        } catch (MissingObjectException e) {
            e.printStackTrace();

            bp.setTeamSize(0);
            bp.setLineOfCodes(0);

            return null;
        }

    }

    // public static void main(String[] args) throws Exception{
    //     String localPath = "/home/workplace/Github/IntelliPipeline/target/IntelliPipeline-1.0-SNAPSHOT/WEB-INF/resources/LocalRepo/Shipping-Test/GitResource/.git";
    //     Git git = Git.open(new File(localPath));
    //
    //     // StringBuffer sb = getExperiencedAuthors(10, git);
    //
    //     Map<String, String> res = compareLocalAndRemote(git);
    //
    //     String[] authors = res.get("Authors").split(" ");
    //
    //     System.out.println("sss");
    // }


}