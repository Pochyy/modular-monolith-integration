package edu.cit.lariosa.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tiangge_feed_cursors")
class TianggeFeedCursor {
    @Id
    private Integer id;

    @Column(name = "cursor_value", nullable = false)
    private String cursorValue;

    public TianggeFeedCursor() {}
    public TianggeFeedCursor(Integer id, String cursorValue) {
        this.id = id;
        this.cursorValue = cursorValue;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCursorValue() { return cursorValue; }
    public void setCursorValue(String cursorValue) { this.cursorValue = cursorValue; }
}
