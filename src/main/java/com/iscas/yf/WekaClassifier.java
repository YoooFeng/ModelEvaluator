package com.iscas.yf;

import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.AttributeSelectedClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.*;
import weka.experiment.InstanceQuery;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToNominal;
import weka.gui.treevisualizer.*;

import javax.servlet.ServletContext;
import java.applet.Applet;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

public class WekaClassifier extends Applet{

    // 将模型文件保存在项目目录下
    // private static String MODEL_STORAGE_DIR = "/home/workplace/Github/IntelliPipeline/target/IntelliPipeline-1.0-SNAPSHOT/WEB-INF/resources/LocalRepo/java/";

    private static String MODEL_STORAGE_DIR = "C:\\ModelEvaluator\\";

    // 模型后缀
    private static String MODEL_EXTENSION = ".model";

    private static File RECORD = new File("C:\\ModelEvaluator\\record.txt");

    private static PrintWriter PW;

    // 从数据库中获取数据实例
    public static Instances getInstanceFromDatabase(String projectName, String language, boolean exclude) throws Exception{
        InstanceQuery query = new InstanceQuery();

        query.setDatabaseURL("jdbc:mysql://localhost:3306/travistorrent_calculated");

        // 为查询配置数据库帐号和密码
        query.setUsername("root");
        query.setPassword("123456");

        // 将目标项目排除在外，用于训练模型
        if(exclude) {
            // 不指定语言的情况
            if(language.equals("")) {
                query.setQuery("select "
                        + "build_id, "
                        + "git_num_all_built_commits, "
                        + "num_all_files_modified, "
                        // + "git_branch, "
                        + "modified_lines, "
                        + "last_build, "
                        + "project_history, "
                        + "project_recent, "
                        + "status "
                        + "from travistorrent_calculated_09_01 "
                        + "where build_id not in"
                        + "(select build_id from travistorrent_calculated_09_01 where project_name='" + projectName + "')"
//                        + "and language='" + language + "'"
                        + ";");
            } else {
                // 排除的同时指定了目标语言
                query.setQuery("select "
                        + "build_id, "
                        + "git_num_all_built_commits, "
                        + "num_all_files_modified, "
                        // + "git_branch, "
                        + "modified_lines, "
                        + "last_build, "
                        + "project_history, "
                        + "project_recent, "
                        + "status "
                        + "from travistorrent_calculated_09_01 "
                        + "where build_id not in"
                        + "(select build_id from travistorrent_calculated_09_01 where project_name='" + projectName + "')"
                        + "and language='" + language + "'"
                        + ";");
            }
        } else {
            // 没有排除项目，看是否指定了项目名和特定语言
            if(projectName.equals("") && language.equals("")) {
                // 全选
                query.setQuery("select "
                        + "build_id, "
                        + "git_num_all_built_commits, "
                        + "num_all_files_modified, "
                        // + "git_branch, "
                        + "modified_lines, "
                        + "last_build, "
                        + "project_history, "
                        + "project_recent, "
                        + "status "
                        + "from travistorrent_calculated_09_01 "
//                        + "where project_name='" + projectName + "'"
//                + "and language=" + language
                        + ";");
            } else if(language.equals("")) {
                query.setQuery("select "
                        + "build_id, "
                        + "git_num_all_built_commits, "
                        + "num_all_files_modified, "
                        // + "git_branch, "
                        + "modified_lines, "
                        + "last_build, "
                        + "project_history, "
                        + "project_recent, "
                        + "status "
                        + "from travistorrent_calculated_09_01 "
                        + "where project_name='" + projectName + "'"
                        + ";");
            } else {
                query.setQuery("select "
                        + "build_id, "
                        + "git_num_all_built_commits, "
                        + "num_all_files_modified, "
                        // + "git_branch, "
                        + "modified_lines, "
                        + "last_build, "
                        + "project_history, "
                        + "project_recent, "
                        + "status "
                        + "from travistorrent_calculated_09_01 "
                        + "where project_name='" + projectName + "'"
                        + "and language='" + language + "'"
                        + ";");
            }
        }

        // 从查询结果中获取数据并返回
        return query.retrieveInstances();
    }

    // 训练数据得到分类器模型, 以 项目名.model 的格式存放在项目本地目录下
    public static Classifier trainModel(Instances trainData) throws Exception {

        // setClassIndex的意思是, 用某一个属性(一列)来代表这一条数据
        // 即指定要预测的某一列属性, 这里指定为 构建的结果(成功或失败)
        // 删除得到记录的第一个属性, 通常是build_id等无关的特征, 删掉不参与构建决策树
        trainData.deleteAttributeAt(0);
        trainData.setClassIndex(trainData.numAttributes() - 1);

        System.out.println(trainData.toSummaryString());
        // String[] options = new String[1];
        // // -U means unpruned tree
        // options[0] = "-U";

        // 使用的模型是J48树, 相关文献指出, Hoeffding tree有更好的效果
        Classifier m_RandomForest = new RandomForest();
        // j48.setOptions(options);
        m_RandomForest.buildClassifier(trainData);

        // 属性列的集合
        // Attribute attribute;

        // 带有特征选择的分类器
        AttributeSelectedClassifier classifier = new AttributeSelectedClassifier();

        // 评价模型分类准确性
        CfsSubsetEval eval = new CfsSubsetEval();

        GreedyStepwise search = new GreedyStepwise();

        // 设置为反向搜索, 从最大子集开始, 逐步减小
        search.setSearchBackwards(true);

        classifier.setClassifier(m_RandomForest);

        classifier.setEvaluator(eval);

        classifier.setSearch(search);

//        System.out.println("================================== 模型训练与十折交叉验证 ===================================");
//        PW.println("================================== 模型训练与十折交叉验证 ===================================");

//        // 十折交叉验证
//        Evaluation evaluation = new Evaluation(trainData);
//        evaluation.crossValidateModel(m_RandomForest, trainData, 5, new Debug.Random(1));
//
//        // System.out.println(evaluation.toSummaryString("Classifier Ten fold " ,true));
//        // 查看模型的验证结果
//        System.out.println(evaluation.toSummaryString());
//        PW.println(evaluation.toSummaryString());
//
//        // 查看准确率和召回率
//        System.out.println(evaluation.toClassDetailsString());
//        PW.println(evaluation.toClassDetailsString());

        return m_RandomForest;
    }

    /**
     * 保存分类器模型
     * @Param Classifier classifier: 分类器
     * @Param String name: 模型名(暂时与项目名称相同)
     * @Param String dir: 存放模型的地址
     * */
    public static void saveModel(Classifier classifier, String name, String dir) {
        // 利用java序列化存储训练好的模型
        try {
            SerializationHelper.write(dir + name + MODEL_EXTENSION, classifier);
            System.out.println("Save model successfully!");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取模型.
     * */
    public static <T> T loadModel(String name, String dir) {
        Classifier classifier = null;
        try {
            classifier = (Classifier) SerializationHelper.read(dir
                    + name + MODEL_EXTENSION);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return (T)classifier;
    }

    /**
     * 进行预测.
     * @Param projectName: 项目名, 利用项目名可以在HQL查找得到构建的历史数据.
     *                     但是未进行的构建需要实时获得相关特征值来进行预测.
     * */
    public static double predict(Classifier model, String buildRecord) {

        // 逗号将三个属性分割, 最后一个status属性值为"?", 不需要加入instance
        String[] strs = buildRecord.split(",");

        // 用于分类的决策因子
        // 近期开发者数量
//        Attribute team_size = new Attribute("team_size");

        Attribute num_built_commits = new Attribute("git_num_all_built_commits");

        // 本次涉及的修改文件数量
        Attribute files_modified = new Attribute("num_all_files_modified");

        // 修改代码行数
        Attribute loc = new Attribute("modified_lines");

        // 构建上下文(上一次构建是否成功)
        Attribute last_build = new Attribute("last_build");

        // 项目构建历史成功率
        Attribute project_history = new Attribute("project_history");

        // 项目构建近期成功率
        Attribute project_recent = new Attribute("project_recent");

        Attribute status = new Attribute("status");
        ArrayList<Attribute> atts = new ArrayList<>();

        // 这里要注意添加的顺序, 与record传过来的严格对应
        atts.add(num_built_commits);
        atts.add(files_modified);
        atts.add(loc);
        atts.add(last_build);
        atts.add(project_history);
        atts.add(project_recent);
        atts.add(status);

        double[] attValues = new double[8];
        // attValues[0] = 0;
        // attValues[1] = 100;
        attValues[0] = Double.parseDouble(strs[0]);
        attValues[1] = Double.parseDouble(strs[1]);
        attValues[2] = Double.parseDouble(strs[2]);
        attValues[3] = Double.parseDouble(strs[3]);
        attValues[4] = Double.parseDouble(strs[4]);
        attValues[4] = Double.parseDouble(strs[5]);
//        attValues[4] = Double.parseDouble(strs[6]);

        // 这里设置的权重(weight)指的是这个instance的权重
        BinarySparseInstance i = new BinarySparseInstance(1.0, attValues);

        // atts.add(new Attribute("team_size", "4"));
        // atts.add(new Attribute("modified_lines", "100"));

        Instances instance = new Instances("TestInstances", atts, 0);
        instance.add(i);
        instance.setClassIndex(instance.numAttributes() - 1);

        try {
            // 进行判断
            Evaluation eval = new Evaluation(instance);

            // 看看测试结果
            System.out.println("Classify only one instance");
            System.out.println(eval.evaluateModelOnce(model, instance.firstInstance()));

            // 预测会得到一个0-1之间的值, 越接近0, 说明失败的概率越高, 越接近1, 说明成功的概率越高.
            return eval.evaluateModelOnce(model, instance.firstInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 预测失败, 返回0L
        return 0L;
    }

    /**
     * 在JFrame框体中初始化决策树可视化图形.
     * @Param String dottyString -
     * */
    public static void visualizeTree(String dottyString) {
        final javax.swing.JFrame jf = new javax.swing.JFrame("Decision Tree");
        jf.setSize(1000, 800);
        jf.getContentPane().setLayout(new BorderLayout());
        TreeVisualizer tv = new TreeVisualizer(null, dottyString, new PlaceNode2());
        jf.getContentPane().add(tv, BorderLayout.CENTER);
        jf.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                jf.dispose();
            }
        });

        jf.setVisible(true);
        tv.fitToScreen();
    }

    /**
     * 更新模型的函数.
     * */
    public static void updateModel() throws Exception{
        Instances trainData = getInstanceFromDatabase("", "java",false);
    }

    /**
     * 返回决策树的字符串表示
     * */
    public static String getTreeString(String projectName, String dir) throws Exception{
        J48 tree = loadModel("java", MODEL_STORAGE_DIR);
        return tree.graph();
    }

    public static void training(String projectName, String language) throws Exception {
        Instances trainData = getInstanceFromDatabase(projectName, language, false);

        Classifier tree = trainModel(trainData);

        saveModel(tree, "RF-all_projects", MODEL_STORAGE_DIR);

//        if(language.equals("")) {
//            saveModel(tree, "RF-all_projects_without_" + projectName.split("/")[1], MODEL_STORAGE_DIR);
//        } else {
//            saveModel(tree, "RF-" + language + "_projects_without_" + projectName.split("/")[1], MODEL_STORAGE_DIR);
//        }

    }

    public static void predicting(String projectName, String language) throws Exception {
        Classifier tree;
        if(language.equals("")) {
            tree = loadModel("RF-all_projects_without_" + projectName.split("/")[1], MODEL_STORAGE_DIR);
        } else {
            tree = loadModel("RF-" + language + "_projects_without_" + projectName.split("/")[1], MODEL_STORAGE_DIR);
        }
//        tree = loadModel("J48-0109", MODEL_STORAGE_DIR);

        System.out.println("================================== 预测相关 ===================================");
        PW.println("================================== 预测相关 ===================================");

        Instances predictData = getInstanceFromDatabase(projectName,language,false);
        predictData.deleteAttributeAt(0);
        predictData.setClassIndex(predictData.numAttributes() - 1);

        System.out.println(predictData.toSummaryString());
        PW.println(predictData.toSummaryString());
        // 初始化的时候也需要数据，以获得数据类型的信息
        Evaluation evaluation = new Evaluation(predictData);

        evaluation.evaluateModel(tree, predictData);

        System.out.println(evaluation.toSummaryString());
        PW.println(evaluation.toSummaryString());

        System.out.println(evaluation.toClassDetailsString());
        PW.println(evaluation.toClassDetailsString());
    }

    public static void predictingAll(Classifier tree, String[] projectNames) throws Exception {

        for(String projectName : projectNames) {

//            if(projectName.equals("makrio/makrio")
//                    || projectName.equals("reficio/p2-maven-plugin")
//                    || projectName.equals("kevinsawicki/http-request")
//                    || projectName.equals("foursquare/fongo")
//                    || projectName.equals("zmoazeni/csscss")
//                    || projectName.equals("RailsApps/rails_apps_composer")
//                    || projectName.equals("Esri/geometry-api-java")
//                    || projectName.equals("june29/horesase-boys")
//                    || projectName.equals("julianhyde/linq4j")
//                    || projectName.equals("collectiveidea/interactor")
//                    || projectName.equals("airlift/slice")
//                    || projectName.equals("sdywcd/jshoper3x")
//                    || projectName.equals("bitsofproof/supernode")
//                    || projectName.equals("JakeWharton/timber")
//                    || projectName.equals("chocoteam/choco3")
//                    || projectName.equals("bemyeyes/bemyeyes-server")
//                    || projectName.equals("eval/envied")
//                    || projectName.equals("MirakelX/mirakel-android")
//                    || projectName.equals("OfficeDev/ews-java-api")
//                    || projectName.equals("palantir/eclipse-typescript")
//                    || projectName.equals("javaee-samples/javaee7-samples")
//                    || projectName.equals("OpenSOC/opensoc-streaming")
//                    || projectName.equals("xerial/sqlite-jdbc")
//                    || projectName.equals("JavaMoney/jsr354-api")
//                    || projectName.equals("JuanitoFatas/fast-ruby")
//                    || projectName.equals("boxen/puppet-osx")
//                    || projectName.equals("inf0rmer/blanket")
//                    || projectName.equals("fazibear/colorize")
//            ) continue;



            Instances predictData = getInstanceFromDatabase(projectName,"",false);

            predictData.deleteAttributeAt(0);
            predictData.setClassIndex(predictData.numAttributes() - 1);


            // 初始化的时候也需要数据，以获得数据类型的信息
            Evaluation evaluation = new Evaluation(predictData);

            evaluation.evaluateModel(tree, predictData);

            System.out.println(evaluation.weightedPrecision());
            PW.println(evaluation.weightedPrecision());

//            System.out.println("ProjectName:" + projectName);
//            PW.println("ProjectName:" + projectName);

//            System.out.println(predictData.toSummaryString());
//            PW.println(predictData.toSummaryString());

//            System.out.println(evaluation.toSummaryString());
//            PW.println(evaluation.toSummaryString());
//
            System.out.println(evaluation.toClassDetailsString());
//            PW.println(evaluation.toClassDetailsString());
//
//            System.out.println("========================================================================");
//            PW.println("========================================================================");

        }

    }

    public static String[] getAllProjectName() throws Exception {
        InstanceQuery query = new InstanceQuery();

        query.setDatabaseURL("jdbc:mysql://localhost:3306/travistorrent_calculated");

        // 为查询配置数据库帐号和密码
        query.setUsername("root");
        query.setPassword("123456");

//        query.setQuery("select "
//                + "distinct(project_name) "
//                + "from travistorrent_calculated_09_01 "
//                + "");
        query.setQuery("select table1.project_name from" +
                "(select project_name, count(distinct(status)) as num" +
                " from travistorrent_calculated_09_01" +
                " group by project_name) as table1" +
                " where table1.num = 2;");

        String str = query.retrieveInstances().attribute("project_name").toString().split("project_name")[1].trim();
        str = str.replace("{","");
        str = str.replace("}","");
        String[] projectNames = str.split(",");
        return projectNames;
    }

    public static void main(String[] args) throws Exception {
        if(!RECORD.exists()) {
            RECORD.createNewFile();
        }

        PW = new PrintWriter(new FileOutputStream(RECORD, true));

//        String[] projectsAndLanguage = {"rails/rails,ruby", "jruby/jruby,ruby", "ruby/ruby,ruby",
//        "apache/jackrabbit-oak,java", "CloudifySource/cloudify,java", "SonarSource/sonarqube,java",
//        "owncloud/android,java", "gradle/gradle,java", "opf/openproject,ruby", "Graylog2/graylog2-server,java"};

//        String[] projectsAndLanguage = {"rails/rails,ruby"};

//        for(String s : projectsAndLanguage) {
//            String project = s.split(",")[0].trim();
//            String lan = s.split(",")[1].trim();
//
//            System.out.println("Project:" + project + ", language:" + lan);
//            PW.println("Project:" + project + ", language:" + lan);
//
//            training(project,"");
//            predicting(project, "");
//
//            training(project, lan);
//            predicting(project, lan);
//        }

        Classifier tree = loadModel("RF-all_projects", MODEL_STORAGE_DIR);
        String[] projectNames = getAllProjectName();

//        projectNames = Arrays.copyOfRange(projectNames, 1213, projectNames.length);
//        int i = 0;
//
//        while(i <= projectNames.length) {
//            String[] sub = Arrays.copyOfRange(projectNames, i, i + 100);
//
//            predictingAll(tree, sub);
//
//            if(i == projectNames.length - 100) break;
//
//            if(i + 100 < projectNames.length) i += 100;
//            else i = projectNames.length - 100;
//
//            System.out.println("i" + i);
//        }

        predictingAll(tree, projectNames);

        PW.close();

//        predict(loadModel("J48-ruby_projects_without_rails", MODEL_STORAGE_DIR),"1,1,2,1,0.9416,1,passed");
//        J48 tree = loadModel("J48-all_projects_without_puppet",MODEL_STORAGE_DIR);
//        visualizeTree(tree.graph());
    }
}