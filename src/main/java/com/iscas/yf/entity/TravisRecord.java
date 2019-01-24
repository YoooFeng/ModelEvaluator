package com.iscas.yf.entity;

public class TravisRecord implements java.io.Serializable{
    // 将爬取的Travis构建记录转化为POJO

    private Integer id;
    private String state;
    private Integer duration;
    private String previousState;
    private String startedAt;
    private String commit;

    // 每一条记录对应一个predict对象
    private BuildPredict buildPredict;

    public TravisRecord(Integer id, String state, Integer duration, String previousState, String startedAt, String commit) {
        super();
        this.id = id;
        this.state = state;
        this.duration = duration;
        this.previousState = previousState;
        this.startedAt = startedAt;
        this.commit = commit;
    }

    public TravisRecord() {
        super();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getPreviousState() {
        return previousState;
    }

    public void setPreviousState(String previousState) {
        this.previousState = previousState;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getCommit() {
        return commit;
    }

    public void setCommit(String commit) {
        this.commit = commit;
    }

    public BuildPredict getBuildPredict() {
        return buildPredict;
    }

    public void setBuildPredict(BuildPredict buildPredict) {
        this.buildPredict = buildPredict;
    }

    @Override
    public String toString(){
        return "[id="+ id
                + " started_time="+ startedAt
                + " commit="+ commit
                + " previous_state=" + previousState
                + " project_recent=" + buildPredict.getProjectRecent()
                + " project_history=" + buildPredict.getProjectHistory()
                + " team_size=" + buildPredict.getTeamSize()
                + " files_modified=" + buildPredict.getFileMofified()
                + " loc=" + buildPredict.getLineOfCodes()
                + " author=" + buildPredict.getCommitAuthor().trim()
                + " state=" + state
                + " prediction=" + buildPredict.getPrediction()
                + "]";
    }
}
