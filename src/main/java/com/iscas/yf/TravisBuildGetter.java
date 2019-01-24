package com.iscas.yf;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.iscas.yf.entity.BuildPredict;
import com.iscas.yf.entity.TravisRecord;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;

public class TravisBuildGetter {

    private static String fileDir = "C:\\yang\\workplace\\TravisPrediction\\";

    private static DecimalFormat decimalFormat = new DecimalFormat("#.####");

    private static String MODEL_STORAGE_DIR = "C:\\ModelEvaluator\\";

//    private static String[] repoSlugs = {"angular%2Fangular.js", "apache%2Fdrill", "apache%2Fstorm",
//            "atom%2Fatom", "GitSquared%2Fedex-ui", "meteor%2Fmeteor", "Netflix%2FHystrix",
//            "prestodb%2Fpresto", "webpack%2Fwebpack", "rails%2Frails"};

    private static String[] repoSlugs = {"angular%2Fangular.js"};

    private static J48 MODEL = WekaClassifier.loadModel("J48", MODEL_STORAGE_DIR);

    public static void crawler() throws Exception {

        for(String project : repoSlugs) {

            String NEXT = "/repo/" + project + "/builds?sort_by=finished_at:desc&limit=50";

            while(NEXT != null) {

                // 初始值
                String s = "https://api.travis-ci.org";

                // 按顺序装入需要的数据，List按照Project进行区分
                List<TravisRecord> travisRecords = new ArrayList<>();

                String query = s + NEXT;

                URL travisUrl = new URL(query);

                HttpURLConnection conn = (HttpURLConnection)travisUrl.openConnection();

                // 设置连接的Header参数
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Travis-API-Version", "3");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_0) AppleWebKit/535.11 (KHTML, like Gecko) Chrome/17.0.963.56 Safari/535.11");
                conn.setRequestProperty("Authorization", "token: doLHU_yE_oSJoj1ph5LQYw");

                Connection.Response res = Jsoup.connect(query)
                        .header("Travis-API-Version", "3")
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_0) AppleWebKit/535.11 (KHTML, like Gecko) Chrome/17.0.963.56 Safari/535.11")
                        .header("Authorization", "token doLHU_yE_oSJoj1ph5LQYw")
                        .header("Accept", "*/*")
//                .header("Accept-Encoding", "gzip, deflate")
//                .header("Accept-Language","zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .timeout(50000).ignoreContentType(true).execute();


                String body = res.body();
                JSONObject json = JSONObject.fromObject(body);

                // 获得所有builds
                JSONArray builds = json.getJSONArray("builds");

                // 先打开文件，不存在就创建
                File file = new File(fileDir + project + ".txt");
                if(!file.exists()) {
                    file.createNewFile();
                }

                // 以append的方式打开文件
                PrintWriter pw = new PrintWriter(new FileOutputStream(file, true));

                float total = 0F;
                float passed = 0F;

                // 遍历对build进行处理
                for(int i = 0; i < builds.size(); i++) {
                    JSONObject b = builds.getJSONObject(i);
                    TravisRecord r = new TravisRecord();

                    if(b.get("state").equals("passed")) {
                        total += 1;
                        passed += 1;
                    } else if(b.get("state").equals("failed")){
                        total +=1;
                    } else {
                        // 如果结果不是passed或者failed，说明构建被跳过或中止，作为噪声数据去掉
                        continue;
                    }

                    r.setCommit((String)(b.getJSONObject("commit"))
                            .get("sha"));

                    r.setDuration( b.getInt("duration"));
                    r.setId(b.getInt("id"));
                    r.setStartedAt(b.getString("started_at"));
                    r.setState(b.getString("state"));
                    r.setPreviousState(b.getString("previous_state"));

                    BuildPredict bp = GitHubRepoService.getPredictByCommit(project, r.getCommit());

                    // 返回的是null说明missing git commit hash
                    if(bp == null) continue;
                    else r.setBuildPredict(bp);

                    if(r.getPreviousState().equals("passed")) {
                        r.getBuildPredict().setLastBuild(1);
                    } else {
                        r.getBuildPredict().setLastBuild(0);
                    }

                    // TODO: 先写再算
//                    // 计算project_history
//                    if(total == 0 || passed == 0) {
//                        r.getBuildPredict().setProjectHistory(0F);
//                        r.getBuildPredict().setProjectRecent(0F);
//                    } else {
//                        r.getBuildPredict().setProjectHistory( Float.valueOf(decimalFormat.format( passed/total )) );
//                    }
//
//                    // 计算project_recent
//                    float passed_recent = 0F;
//                    // 如果前面的构建次数多于或等于五次
//                    if(travisRecords.size() >= 5) {
//                        for(int f = travisRecords.size() - 1; f >= travisRecords.size() - 5; f--) {
//                            if(travisRecords.get(f).getState().equals("passed")) {
//                                passed_recent += 1;
//                            }
//                        }
//                        r.getBuildPredict().setProjectRecent( Float.valueOf(decimalFormat.format( passed_recent/5F )) );
//                    } else {
//                        // 不足五次，那么全都算上
//                        for(int f = travisRecords.size() - 1; f >= 0; f--) {
//                            if(travisRecords.get(f).getState().equals("passed")) {
//                                passed_recent += 1;
//                            }
//                        }
//                        r.getBuildPredict().setProjectRecent( Float.valueOf(decimalFormat.format( passed_recent/(float)travisRecords.size() )) );
//                    }

//                    J48 tree = WekaClassifier.loadModel("J48", MODEL_STORAGE_DIR);
//                    r.getBuildPredict().setPreResult(WekaClassifier.predict(tree, r.getBuildPredict().toString()));

                    // 把爬取下来的构建记录写进文件中进行持久化保存，包括特征值
                    pw.println(r.toString());

                    travisRecords.add(r);
                }

                System.out.println(json.toString());

                pw.close();

                NEXT = json.getJSONObject("@pagination").getJSONObject("next").getString("@href");

                // 每次爬取完一个页面都休息10-20秒，防止被ban
                TimeUnit.SECONDS.sleep(10 + (int)(Math.random() * 11));
            }
        }
    }

    /**
     * 读取文件计算两个影响因子,预测结果
     * */
    public static void processing() throws Exception{

        double truePositive = 0D;
        double falsePositive = 0D;
        double trueNegative = 0D;
        double falseNegative = 0D;
        List<String> cal_lines = new ArrayList<>();

        for(String project : repoSlugs) {
            File file = new File(fileDir + project + ".txt");

            // 内存足够直接将所有行读取到内存中
            final List<String> lines = FileUtils.readLines(file, Charsets.UTF_8);

            // 从文件末尾开始处理
            for(int i = lines.size() - 2; i >= 0; i--) {

                Float total = 0F;
                Float success = 0F;
                Float flag = 0F;
                Float recentSuccess = 0F;

                // 从后往前读取记录
                String record = lines.get(i);
                // 计算projectRecent
                // 前置构建不足五次
                if(lines.size() - i < 6) {
                    for(int j = i + 1; j <= lines.size() - 1; j++) {
                        flag += 1;
                        if(lines.get(j).contains("state=passed")) recentSuccess += 1;
                    }
                    if(recentSuccess != 0)  record = record.replace("project_recent=0.0", "project_recent=" + decimalFormat.format(recentSuccess/flag));

                } else {
                    for(int j = i + 1; j <= i + 5; j++) {
                        if(lines.get(j).contains("state=passed"))   recentSuccess += 1;
                    }
                    if(recentSuccess != 0)  record = record.replace("project_recent=0.0", "project_recent=" + decimalFormat.format(recentSuccess/5F));
                }

                for(int k = i + 1; k <= lines.size() - 1; k++) {
                    total += 1;
                    if(lines.get(k).contains("state=passed")) success += 1;
                }
                if(success != 0) record = record.replace("project_history=0.0", "project_history=" + decimalFormat.format(success/total));

                // 空格分割
                String[] str = record.split(" ");
                String lastBuild = (str[3].split("=")[1].equals("passed") ? "1" : "0");
                String projectRecent = str[4].split("=")[1];
                String projectHistory = str[5].split("=")[1];
                String filesModified = str[7].split("=")[1];
                String loc = str[8].split("=")[1];

                double prediction = WekaClassifier.predict(MODEL,
                        filesModified + ","
                                + loc + ","
                                + lastBuild + ","
                                + projectHistory + ","
                                + projectRecent + ","
                                +",?");

                if(prediction >= 0.5) {
                    record = record.replace("prediction=failed", "prediction=passed");

                    // 实际结果也为passed
                    if(str[10].contains("passed")) {
                        truePositive += 1;
                    } else {
                        falseNegative += 1;
                    }
                } else {
                    if(str[10].contains("passed")) {
                        falsePositive += 1;
                    } else {
                        trueNegative += 1;
                    }
                }
                cal_lines.add(record);
            }
            String precision =  decimalFormat.format(truePositive / (truePositive + falsePositive));
            String recall = decimalFormat.format(truePositive / (truePositive + falseNegative));

            // 先打开文件，不存在就创建
            File calFile = new File(fileDir + project + "_cal.txt");
            if(!file.exists()) {
                file.createNewFile();
            }

            // 以append的方式打开文件
            PrintWriter pw = new PrintWriter(new FileOutputStream(calFile, true));

            for(String line : cal_lines) {
                pw.println(line);
            }
            pw.println("Precision:" + precision + " Recall:" + recall);
            System.out.println("Precision:" + precision + " Recall:" + recall);
            pw.close();
        }
    }

    public static void main(String[] args) throws Exception{
        // write your code here
        crawler();
//        processing();
    }
}
