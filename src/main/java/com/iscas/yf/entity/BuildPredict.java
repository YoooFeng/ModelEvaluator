package com.iscas.yf.entity;

public class BuildPredict {

    private String commitHash;

    // 五个特征值
    private Integer teamSize;
    private Integer lineOfCodes;
    private int lastBuild;
    private Float projectRecent = 0F;
    private Float projectHistory = 0F;
    private Integer fileMofified = 0;

    // 该次构建实际的结果和预期的结果
    private Integer trResult = 0;
    private Double preResult = 0D;

    // 预先记录的信息，后续可使用
    private String commitAuthor = "";

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public int getLastBuild() {
        return lastBuild;
    }

    public void setLastBuild(int lastBuild) {
        this.lastBuild = lastBuild;
    }

    public Float getProjectRecent() {
        return projectRecent;
    }

    public void setProjectRecent(Float projectRecent) {
        this.projectRecent = projectRecent;
    }

    public Float getProjectHistory() {
        return projectHistory;
    }

    public void setProjectHistory(Float projectHistory) {
        this.projectHistory = projectHistory;
    }

    public Integer getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(Integer teamSize) {
        this.teamSize = teamSize;
    }

    public Integer getLineOfCodes() {
        return lineOfCodes;
    }

    public void setLineOfCodes(Integer lineOfCodes) {
        this.lineOfCodes = lineOfCodes;
    }

    public Integer getTrResult() {
        return trResult;
    }

    public void setTrResult(Integer trResult) {
        this.trResult = trResult;
    }

    public Double getPreResult() {
        return preResult;
    }

    public void setPreResult(Double predResult) {
        this.preResult = predResult;
    }

    public String getPrediction() {
        if(this.getPreResult() < 0.5) return "failed";
        else return "passed";
    }

    public String getCommitAuthor() {
        return commitAuthor;
    }

    public void setCommitAuthor(String commitAuthor) {
        this.commitAuthor = commitAuthor;
    }

    public Integer getFileMofified() {
        return fileMofified;
    }

    public void setFileMofified(Integer fileMofified) {
        this.fileMofified = fileMofified;
    }

    // 按照Weka Instance的格式重写toString函数
    @Override
    public String toString() {
        // "team_size, loc, last_build, project_history, project_recent, ?(build_status_to_predict)"
        return this.getTeamSize()
                + "," + this.getLineOfCodes()
                + "," + this.getLastBuild()
                + "," + this.getProjectHistory()
                + "," + this.getProjectRecent()
                + ",?";
    }
}
