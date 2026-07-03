package com.itgeo.fitmate.common.response;

/**
 * 统一接口响应结构。
 *
 * status 表示业务状态码，msg 表示提示信息，data 表示响应数据，
 * ok 为历史兼容字段，当前通常不再使用。
 */
public class LeeResult {

    /** 业务状态码。 */
    private Integer status;

    /** 响应消息。 */
    private String msg;

    /** 响应数据载荷。 */
    private Object data;
    
    /** 历史兼容字段，当前通常不使用。 */
    private String ok;

    /**
     * 按指定状态码构建响应。
     */
    public static LeeResult build(Integer status, String msg, Object data) {
        return new LeeResult(status, msg, data);
    }

    /**
     * 按指定状态码构建响应，并附带兼容字段。
     */
    public static LeeResult build(Integer status, String msg, Object data, String ok) {
        return new LeeResult(status, msg, data, ok);
    }
    
    /**
     * 构建成功响应并携带数据。
     */
    public static LeeResult ok(Object data) {
        return new LeeResult(data);
    }

    /**
     * 构建空数据成功响应。
     */
    public static LeeResult ok() {
        return new LeeResult(null);
    }
    
    /**
     * 构建通用失败响应。
     */
    public static LeeResult errorMsg(String msg) {
        return new LeeResult(500, msg, null);
    }

    /**
     * 构建用户票据校验失败响应。
     */
    public static LeeResult errorUserTicket(String msg) {
        return new LeeResult(557, msg, null);
    }
    
    /**
     * 构建参数校验错误响应。
     */
    public static LeeResult errorMap(Object data) {
        return new LeeResult(501, "error", data);
    }
    
    /**
     * 构建登录令牌校验失败响应。
     */
    public static LeeResult errorTokenMsg(String msg) {
        return new LeeResult(502, msg, null);
    }
    
    /**
     * 构建服务异常响应。
     */
    public static LeeResult errorException(String msg) {
        return new LeeResult(555, msg, null);
    }
    
    /**
     * 构建 QQ 用户校验失败响应。
     */
    public static LeeResult errorUserQQ(String msg) {
        return new LeeResult(556, msg, null);
    }

    public LeeResult() {

    }

    public LeeResult(Integer status, String msg, Object data) {
        this.status = status;
        this.msg = msg;
        this.data = data;
    }
    
    public LeeResult(Integer status, String msg, Object data, String ok) {
        this.status = status;
        this.msg = msg;
        this.data = data;
        this.ok = ok;
    }

    public LeeResult(Object data) {
        this.status = 200;
        this.msg = "OK";
        this.data = data;
    }

    public Boolean isOK() {
        return this.status == 200;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

	public String getOk() {
		return ok;
	}

	public void setOk(String ok) {
		this.ok = ok;
	}

}
