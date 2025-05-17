package com.example.noac.resp;

public class CommonResp<T>{

    private T content;

    private String message = "操作成功";

    private boolean success = true;


    @Override
    public String toString() {
        return "CommonResp{" +
                "content=" + content +
                ", message='" + message + '\'' +
                ", success=" + success +
                '}';
    }

    public CommonResp() {
    }
    public CommonResp(T content) {
        this.content = content;
    }

    public CommonResp(String message) {
        this.message = message;
    }

    public CommonResp(String message, boolean success) {
        this.message = message;
        this.success = success;
    }




    public CommonResp(String message, boolean success, T content) {
        this.message = message;
        this.success = success;
        this.content = content;
    }
    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }


}
