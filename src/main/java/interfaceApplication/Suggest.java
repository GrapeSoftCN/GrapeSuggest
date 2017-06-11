package interfaceApplication;

import java.io.FileInputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import apps.appsProxy;
import authority.privilige;
import database.db;
import esayhelper.CacheHelper;
import esayhelper.JSONHelper;
import esayhelper.StringHelper;
import esayhelper.TimeHelper;
import esayhelper.jGrapeFW_Message;
import interrupt.interrupt;
import model.SuggestModel;
import nlogger.nlogger;
import rpc.execRequest;
import security.codec;
import session.session;
import sms.ruoyaMASDB;

public class Suggest {
	private static JSONObject _obj;
	private static SuggestModel model;
	private static session session;
	private static int APPID = appsProxy.appid();
	private static JSONObject UserInfo = new JSONObject();
	private static HashMap<String, Object> map;
	private List<String> imgList = new ArrayList<String>();
	private List<String> videoList = new ArrayList<String>();

	static {
		map = new HashMap<String, Object>();
		_obj = new JSONObject();
		model = new SuggestModel();
		session = new session();
	}

	public Suggest() {
		UserInfo = session.getSession((String) execRequest.getChannelValue("sid"));
		nlogger.logout("userinfo:" + UserInfo);
	}

	/**
	 * 新增咨询建议信息 [支持实名咨询，匿名咨询] 实名认证除了验证手机号，还需要验证当前用户的身份号信息
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param info
	 *            （咨询件信息数据，内容进行base64编码）
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String AddSuggest(String info) {
		// int code = 99;
		String result = resultMessage(99);
		JSONObject object = model.check(info, def());
		if (UserInfo != null) {
			if (object != null) {
				try {
					String content = (String) object.get("content");
					String key = appsProxy
							.proxyCall(getHost(0), APPID + "/106/KeyWords/CheckKeyWords/" + content, null, "")
							.toString();
					JSONObject keywords = JSONHelper.string2json(key);
					long codes = (Long) keywords.get("errorcode");
					if (codes == 3) {
						return resultMessage(3);
					}
					int mode = Integer.parseInt(object.get("mode").toString());
					if (mode == 1) {
						// 实名验证,发送短信验证码
						result = RealName(object);
					} else {
						object.put("content", encode(content));
						result = insert(object.toString());
					}
				} catch (Exception e) {
					nlogger.logout(e);
					result = resultMessage(99);
				}
			}
		} else {
			return resultMessage(2);
		}
		return result;
	}

	/**
	 * 咨询建议回复
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param id
	 *            咨询件id
	 * @param replyContent
	 *            回复内容[内容进行base64编码]
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String Reply(String id, String replyContent) {
		JSONObject object = JSONHelper.string2json(replyContent);
		int code = 99;
		if (object != null) {
			try {
				if (!object.containsKey("replyTime")) {
					object.put("replyTime", TimeHelper.nowMillis());
				}
				if (!object.containsKey("state")
						|| (object.containsKey("state") && !("2").equals(object.get("state")))) {
					object.put("state", 2);
				}
				object.put("replyContent", encode((String) object.get("replyContent")));
				object = JSONHelper.string2json(update(id, object.toString()));
				code = Integer.parseInt(String.valueOf((Long) object.get("errorcode")));
			} catch (Exception e) {
				nlogger.logout(e);
				code = 99;
			}
		}
		return resultMessage(code, "咨询建议件回复成功");
	}

	/**
	 * 分页显示所有的咨询件数据
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param ids
	 *            当前页
	 * @param pageSize
	 *            每页数据量
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String Page(int ids, int pageSize) {
		JSONObject object = new JSONObject();
		JSONArray array = model.getdb().page(ids, pageSize);
		object.put("totalSize", (int) Math.ceil((double) array.size() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(decode(array)));
		return resultMessage(object);
	}

	/**
	 * 按条件分页显示咨询件数据
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param ids
	 * @param pageSize
	 * @param info
	 *            如果条件为null,默认查询已回复咨询件,即满足state=2的数据
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String PageBy(int ids, int pageSize, String info) {
		db db = model.getdb();
		JSONObject object = new JSONObject();
		JSONArray array = new JSONArray();
		JSONObject obj = JSONHelper.string2json(info);
		if (obj != null) {
			db.and();
			for (Object objs : object.keySet()) {
				if (objs.equals("_id")) {
					db.eq("_id", new ObjectId(object.get("_id").toString()));
				}
				db.eq(objs.toString(), object.get(objs.toString()));
			}
			array = db.page(ids, pageSize);
		} else {
			array = model.getdb().eq("state", 2).select();
		}
		object.put("totalSize", (int) Math.ceil((double) array.size() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(decode(array)));
		return resultMessage(object);
	}

	/**
	 * 新增操作
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param info
	 * @return
	 *
	 */
	public String insert(String info) {
		int code = 99;
		try {
			code = model.getdb().data(info).insertOnce() != null ? 0 : 99;
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return resultMessage(code, "提交成功");
	}

	/**
	 * 修改操作
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param id
	 * @param info
	 * @return
	 *
	 */
	public String update(String id, String info) {
		int code = 99;
		try {
			code = model.getdb().eq("_id", new ObjectId(id)).data(info).update() != null ? 0 : 99;
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return resultMessage(code, "修改成功");
	}

	/**
	 * 审核咨询件回复情况
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param id
	 * @param info
	 *            不包含审核状态，则默认为通过审核 即 reviewstate为0
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String Review(String id, String info) {
		int code = 99;
		int role = getRoleSign();
		if (role >= 4) {
			JSONObject obj = JSONHelper.string2json(info);
			if (obj != null) {
				try {
					if (!obj.containsKey("reviewtime")) {
						obj.put("reviewtime", TimeHelper.nowMillis());
					}
					if (!obj.containsKey("reviewstate")) {
						obj.put("reviewstate", 0);
					}
					obj.put("reviewContent", encode((String) obj.get("reviewContent")));
					obj = JSONHelper.string2json(update(id, obj.toString()));
					code = Integer.parseInt(String.valueOf((Long) obj.get("errorcode")));
				} catch (Exception e) {
					nlogger.logout(e);
					code = 99;
				}
			}
		}
		return resultMessage(code, "审核企业管理员回复咨询件操作成功");
	}

	/**
	 * 咨询回复率
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param id
	 * @param info
	 * @return 百分比
	 *
	 */
	public String SugPercent(String Info) {
		String rs = "0";
		float Percent = getCount(JSONHelper.string2json(Info));
		float Count = SugCount();
		if (Count != 0.0) {
			rs = String.format("%.2f", (double) (Percent / Count));
		}
		return resultMessage(0, rs);
	}

	/**
	 * 按状态统计咨询件
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 *       已受理咨询件state:1；已办结咨询件state:2
	 * @return
	 *
	 */
	public String count(String info) {
		long counts = 0;
		JSONObject object = JSONHelper.string2json(info);
		try {
			if (object != null && object.containsKey("state")) {
				counts = model.getdb().eq("state", object.get("state")).count();
			}
		} catch (Exception e) {
			nlogger.logout(e);
			counts = 0;
		}
		return resultMessage(0, String.valueOf(counts));
	}

	/**
	 * 验证用户提交的验证码
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param ckcode
	 * @param IDCard
	 * @return
	 *
	 */
	public String Resume(String ckcode, String IDCard) {
		String string = resultMessage(99);
		if (UserInfo != null) {
			if (IDCard.equals(UserInfo.get("IDCard"))) {
				int code = interrupt._resume(ckcode, UserInfo.get("mobphone").toString(), String.valueOf(APPID));
				switch (code) {
				case 0:
					string = resultMessage(5);
					break;
				case 1:
					string = resultMessage(6);
					break;
				}
				string = resultMessage(0, "咨询建议提交成功");
			}
		}
		return string;
	}

	private HashMap<String, Object> def() {
		map.put("ownid", appsProxy.appid());
		map.put("name", UserInfo != null ? UserInfo.get("name") : "");
		map.put("consult", "");
		map.put("attrImg", ""); // 图片附件
		map.put("attrVideo", ""); // 视频附件
		map.put("mode", 0);
		map.put("state", 0);
		map.put("time", TimeHelper.nowMillis());
		map.put("replyContent", ""); // 回复内容
		map.put("replyTime", 0); // 回复时间
		map.put("reviewstate", 0); // 审核回复状态
		map.put("reviewContent", ""); // 审核回复 内容
		map.put("reviewstate", 0); // 审核回复数据的时间
		return map;
	}

	/**
	 * 实名认证
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param object
	 *            咨询建议信息
	 * @param obj
	 *            用户信息
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	private String RealName(JSONObject object) {
		int code = 99;
		// 发送验证码
		String ckcode = getValidateCode();
		nlogger.logout("验证码：" + ckcode);
		try {
			String phone = UserInfo.get("mobphone").toString();
			if (SendVerity(phone, "验证码:" + ckcode) == 0) {
				object.put("content", encode(object.get("content").toString())); // 对咨询建议内容进行编码
				String nextstep = APPID + "/108/Suggest/insert/" + object.toString();
				JSONObject object2 = interrupt._exist(phone, String.valueOf(APPID));
				if (object2 != null) {
					code = interrupt._clear(phone, String.valueOf(APPID)) == true ? 0 : 99;
				}
				if (code == 0) {
					boolean flag = interrupt._breakMust(ckcode, phone, nextstep, String.valueOf(APPID));
					code = flag ? 0 : 99;
				}
			}
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return resultMessage(code, "验证码发送成功");
	}

	/**
	 * 获取验证码
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @return
	 *
	 */
	private String getValidateCode() {
		String num = "";
		for (int i = 0; i < 6; i++) {
			num = num + String.valueOf((int) Math.floor(Math.random() * 9 + 1));
		}
		return num;
	}

	/**
	 * 发送验证码，设置每天的接受上限
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param phone
	 * @param text
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	private int SendVerity(String phone, String text) {
		session session = new session();
		String time = "";
		String currenttime = TimeHelper.stampToDate(TimeHelper.nowMillis()).split(" ")[0];
		int count = 0;
		JSONObject object = new JSONObject();
		if (session.get(phone) != null) {
			object = JSONHelper.string2json(session.get(phone).toString());
			count = Integer.parseInt(object.get("count").toString()); // 次数
			time = TimeHelper.stampToDate(Long.parseLong(object.get("time").toString()));
			time = time.split(" ")[0];
			if (currenttime.equals(time) && count == 5) {
				return 4;
			}
		}
		String tip = ruoyaMASDB.sendSMS(phone, text);
		count++;
		object.put("count", count + "");
		object.put("time", TimeHelper.nowMillis());
		session.setget(phone, object);
		return tip != null ? 0 : 99;
	}

	/**
	 * 咨询件统计[所有件统计]
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @return
	 *
	 */
	private float SugCount() {
		return (float) model.getdb().count();
	}

	private float getCount(JSONObject obj) {
		db db = model.getdb();
		JSONObject object = new JSONObject();
		if (obj != null) {
			for (Object objs : obj.keySet()) {
				db.and();
				if ("time".equals(objs.toString()) || "replyTime".equals(objs.toString())) {
					object = getTime(obj.get(objs.toString()).toString());
					db.gte(obj.toString(), object.get("start")).lte(obj.toString(), object.get("start"));
				} else {
					db.eq(objs.toString(), obj.get(objs));
				}
			}
		} else {
			db.eq("state", 2);
		}
		return (float) db.count();
	}

	@SuppressWarnings("unchecked")
	private JSONObject getTime(String times) {
		JSONObject object = new JSONObject();
		long starts = 0, ends = 0;
		String start = times.split("~")[0];
		String end = times.split("~")[1];
		if (start.contains(" ")) {
			try {
				starts = TimeHelper.dateToStamp(start);
				ends = TimeHelper.dateToStamp(end);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		} else {
			starts = Long.parseLong(start);
			ends = Long.parseLong(end);
		}
		object.put("start", starts);
		object.put("end", ends);
		return object;
	}

	/**
	 * 根据角色plv，获取角色级别
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @return
	 *
	 */
	private int getRoleSign() {
		int roleSign = 0; // 游客
		String sid = (String) execRequest.getChannelValue("sid");
		if (!sid.equals("0")) {
			try {
				privilige privil = new privilige(sid);
				int roleplv = privil.getRolePV();
				if (roleplv >= 1000 && roleplv < 3000) {
					roleSign = 1; // 普通用户即企业员工
				}
				if (roleplv >= 3000 && roleplv < 5000) {
					roleSign = 2; // 栏目管理员
				}
				if (roleplv >= 5000 && roleplv < 8000) {
					roleSign = 3; // 企业管理员
				}
				if (roleplv >= 8000 && roleplv < 10000) {
					roleSign = 4; // 监督管理员
				}
				if (roleplv >= 10000) {
					roleSign = 5; // 总管理员
				}
			} catch (Exception e) {
				nlogger.logout(e);
				roleSign = 0;
			}
		}
		return roleSign;
	}

	/**
	 * 对提交的咨询内容进行编码
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param Content
	 * @return
	 *
	 */
	private String encode(String Content) {
		return codec.encodebase64(Content);
	}

	/**
	 * 对提交的咨询内容进行解码
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param array
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	private JSONArray decode(JSONArray array) {
		if (array.size() == 0) {
			return array;
		}
		JSONArray array2 = new JSONArray();
		JSONObject object = new JSONObject();
		for (Object obj : array) {
			object = (JSONObject) obj;
			object = decode(object);
			array2.add(object);
		}
		return array2;
	}

	@SuppressWarnings("unchecked")
	private JSONObject decode(JSONObject object) {
		if (object.containsKey("content") && object.get("content") != null) {
			object.put("content", codec.decodebase64(object.get("content").toString()));
		}
		if (object.containsKey("replyContent") && object.get("replyContent") != null) {
			object.put("replyContent", codec.decodebase64(object.get("replyContent").toString()));
		}
		if (object.containsKey("reviewContent") && object.get("reviewContent") != null) {
			object.put("reviewContent", codec.decodebase64(object.get("reviewContent").toString()));
		}
		return object;
	}

	@SuppressWarnings("unchecked")
	private JSONArray getImg(JSONArray array) {
		JSONArray array2 = null;
		if (array != null) {
			try {
				array2 = new JSONArray();
				if (array.size() == 0) {
					return array;
				}
				for (int i = 0; i < array.size(); i++) {
					JSONObject object = (JSONObject) array.get(i);
					array2.add(getImg(object));
				}
			} catch (Exception e) {
				array2 = null;
			}
		}
		return array2;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getImg(JSONObject object) {
		if (object != null) {
			try {
				if (object.containsKey("attr") && !("").equals(object.get("attr").toString())) {
					String attr = object.get("attr").toString();
					String[] value = attr.split(",");
					for (int i = 0; i < value.length; i++) {
						object = getFile("attrFile" + i, value[i], object);
					}
				}
				if (imgList.size() != 0) {
					object.put("image", StringHelper.join(imgList));
				} else {
					object.put("image", "");
				}
				if (videoList.size() != 0) {
					object.put("video", StringHelper.join(videoList));
				} else {
					object.put("video", "");
				}
			} catch (Exception e) {
				object = null;
			}
		}
		return object;
	}
	@SuppressWarnings("unchecked")
	private JSONObject getFile(String key, String imgid, JSONObject object) {
		CacheHelper cache = new CacheHelper();
		if (object != null && !("").equals(object)) {
			if (!("").equals(imgid)) {
				String fileInfo = "";
				if (cache.get(imgid) != null) {
					fileInfo = cache.get(imgid).toString();
				} else {
					// 获取文件对象getAppIp("file")
					String imgurl = "http://" + getAppIp("file").split("/")[1];
					fileInfo = appsProxy.proxyCall(imgurl, appsProxy.appid() + "/24/Files/getFile/" + imgid, null, "").toString();
					if (!("").equals(fileInfo) && fileInfo != null) {
						fileInfo = JSONHelper.string2json(fileInfo).get("message").toString();
						cache.setget(imgid, fileInfo);
					}
				}
				if (("").equals(fileInfo)) {
					object.put(key, "");
				} else {
					JSONObject object2 = JSONHelper.string2json(fileInfo);
					object.put(key, object2);
					if ("1".equals(object2.get("filetype").toString())) {
						imgList.add("http://" + getAppIp("file").split("/")[1] + object2.get("filepath").toString());
					}
					if ("2".equals(object2.get("filetype").toString())) {
						videoList.add("http://" + getAppIp("file").split("/")[1] + object2.get("filepath").toString());
					}
				}
			}
		}
		return object;
	}
	/**
	 * 获取URLConfig.properties配置文件中内网url地址，外网url地址
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param key
	 * @return
	 *
	 */
	private String getAppIp(String key) {
		String value = "";
		try {
			Properties pro = new Properties();
			pro.load(new FileInputStream("URLConfig.properties"));
			value = pro.getProperty(key);
		} catch (Exception e) {
			value = "";
		}
		return value;
	}

	/**
	 * 获取应用url[内网url或者外网url]，0表示内网，1表示外网
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param signal
	 *            0或者1 0表示内网，1表示外网
	 * @return
	 *
	 */
	private String getHost(int signal) {
		String host = null;
		try {
			if (signal == 0 || signal == 1) {
				host = getAppIp("host").split("/")[signal];
			}
		} catch (Exception e) {
			nlogger.logout(e);
			host = null;
		}
		return host;
	}

	private String resultMessage(int num) {
		return resultMessage(num, "");
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONObject object) {
		if (object == null) {
			object = new JSONObject();
		}
		_obj.put("records", object);
		return resultMessage(0, _obj.toString());
	}

	private String resultMessage(int num, String message) {
		String msg = "";
		switch (num) {
		case 0:
			msg = message;
			break;
		case 1:
			msg = "";
			break;
		case 2:
			msg = "请登录之后，再进行咨询";
			break;
		case 3:
			msg = "咨询信息内容含有敏感词";
			break;
		case 4:
			msg = "您今日短信接收次数已达上限";
			break;
		case 5:
			msg = "数据不存在";
			break;
		case 6:
			msg = "验证码错误";
			break;
		default:
			msg = "其他操作异常";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
