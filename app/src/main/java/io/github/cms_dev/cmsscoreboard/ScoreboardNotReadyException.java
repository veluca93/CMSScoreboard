package io.github.cms_dev.cmsscoreboard;

public class ScoreboardNotReadyException extends Exception {
    public ScoreboardStatus status;

    public ScoreboardNotReadyException(String message, ScoreboardStatus status) {
        super(message);
        this.status = status;
    }
}
