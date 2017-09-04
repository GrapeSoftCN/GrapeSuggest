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

import JGrapeSystem.jGrapeFW_Message;
import apps.appsProxy;
import authority.privilige;
import database.DBHelper;
import database.db;
import interrupt.interrupt;
import json.JSONHelper;
import model.SuggestModel;
import nlogger.nlogger;
import rpc.execRequest;
import security.codec;
import session.session;
import sms.ruoyaMASDB;
import string.StringHelper;
import time.TimeHelper;

public class Suggest {
	private JSONObject _obj;
	private SuggestModel model;
	private DBHelper sug;
	private session se;
	private int APPID = appsProxy.appid();
	private JSONObject UserInfo = null;
	private HashMap<String, Object> map;
	private List<String> imgList;
	private List<String> videoList;
	private String SID = null;

	public Suggest() {
		sug = new DBHelper(appsProxy.configValue().getString("db"), "suggest");
		UserInfo = new JSONObject();
		map = new HashMap<String, Object>();
		_obj = new JSONObject();
		model = new SuggestModel();
		se = new session();
		SID = (String)execRequest.getChannelValue("sid");
		if (SID != null) {
			UserInfo = se.getSession(SID);
		}
	}

	public db getdb() {
		return sug.bind(String.valueOf(appsProxy.appid()));
	}

	/**
	 * 新增咨询建议信息
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
		String result = resultMessage(99);
		JSONObject object = model.check(info, def());
		// if (UserInfo == null) {
		// return resultMessage(2);
		// }
		if (object == null) {
			return resultMessage(1);
		}
		try {
			String content = (String) object.get("content");
			content = codec.DecodeHtmlTag(content);
			content = codec.decodebase64(content);
			object.put("content", content);
			result = add(object);
		} catch (Exception e) {
			nlogger.logout(e);
			result = resultMessage(99);
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
		int role = getRoleSign();
		if (role == 6) {
			return resultMessage(7);
		}
		JSONObject object = JSONHelper.string2json(replyContent);
		int code = 99;
		if (object != null) {
			try {
				if (!object.containsKey("replyTime")) {
					object.put("replyTime", TimeHelper.nowMillis());
				}
				if (!object.containsKey("state")
						|| (object.containsKey("state") && !("2").equals(object.get("state").toString()))) {
					object.put("state", 2);
				}
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
	/*
	 * public String Page(int ids, int pageSize) { long totalSize = 0,total = 0;
	 * JSONArray array = new JSONArray(); int role = getRoleSign(); try { db db
	 * = getdb(); // 获取角色权限 if (role == 6 || role == 5 || role == 4) {
	 * db.desc("time"); } else if (role == 3 || role == 2 || role == 1) {
	 * db.eq("wbid", (String) UserInfo.get("currentWeb")); db.desc("time"); }
	 * else { db.eq("slevel", 0); db.desc("time"); } array =
	 * db.desc("time").page(ids, pageSize); totalSize =
	 * db.dirty().pageMax(pageSize); total = db.count(); db.clear(); } catch
	 * (Exception e) { nlogger.logout(e); } return pageShow(array, ids,
	 * pageSize, total, totalSize); }
	 */

	public String Page(int idx, int pageSize) {
		return PageBy(idx, pageSize, null);
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

	public String PageBy(int idx, int pageSize, String info) {
		int role = getRoleSign();
		long totalSize = 0, total = 0;
		JSONArray data = null;
		db db = getdb();
		if (info != null) {
			JSONArray conds = JSONHelper.string2array(info);
			conds = getCond(conds);
			db = db.where(conds);
		}
		switch (role) {
		case 6: // jw
		case 5: // 管理员
		case 4:
			break;
		case 3: // 网站管理员
		case 2:
		case 1:
			String curweb = (String) UserInfo.get("currentWeb");
			if (curweb != null) {
				String webTree = (String) appsProxy.proxyCall("/GrapeWebInfo/WebInfo/getWebTree/" + curweb, null, null);
				String[] webtree = webTree.split(",");
				db.or();
				for (String string : webtree) {
					db.eq("wbid", string);
				}
			} else {
				db.eq("wbid", "");
			}
			break;
		default:
			db.eq("slevel", 0);
			break;
		}
		data = db.dirty().desc("time").page(idx, pageSize);
		total = db.dirty().count();
		totalSize = db.pageMax(pageSize);
		db.clear();
		return pageShow(data, idx, pageSize, total, totalSize);
	}

	@SuppressWarnings("unchecked")
	private JSONArray getCond(JSONArray condArray) {
		JSONObject obj, userInfo;
		String key, value, id = null;
		if (condArray != null && condArray.size() != 0) {
			int l = condArray.size();
			for (int i = 0; i < l; i++) {
				obj = (JSONObject) condArray.get(i);
				key = obj.getString("field");
				if (key.equals("userid")) {
					value = obj.getString("value");
					userInfo = se.getSession(value);
					id = (userInfo != null && userInfo.size() != 0)
							? ((JSONObject) userInfo.get("_id")).getString("$oid") : " ";
					obj.put("value", id);
				}
				condArray.set(i, obj);
			}
		}
		return condArray;
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
			code = getdb().data(info).insertOnce() != null ? 0 : 99;
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
		String reply;
		int role = getRoleSign();
		if (role == 6) {
			return resultMessage(7);
		}
		int code = 99;
		try {
			JSONObject object = JSONObject.toJSON(info);
			if (object != null && object.size() != 0) {
				if (object.containsKey("content")) {
					reply = object.getString("content");
					reply = codec.DecodeHtmlTag(reply);
					reply = codec.decodebase64(reply);
					object.escapeHtmlPut("content", reply);
				}
				if (object.containsKey("replyContent")) {
					reply = object.getString("replyContent");
					reply = codec.DecodeHtmlTag(reply);
					reply = codec.decodebase64(reply);
					object.escapeHtmlPut("replyContent", reply);
				}
			}
			code = getdb().eq("_id", new ObjectId(id)).data(object).update() != null ? 0 : 99;
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
	 *            不包含审核状态,则默认为通过审核 ,即 reviewstate为0（内容进行base64编码）
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String Review(String id, String info) {
		int code = 99;
		int role = getRoleSign();
		if (role == 6) {
			return resultMessage(7);
		}
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
				counts = getdb().eq("state", object.get("state")).count();
			}
		} catch (Exception e) {
			nlogger.logout(e);
			counts = 0;
		}
		return resultMessage(0, String.valueOf(counts));
	}

	/**
	 * 待处理咨询统计
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @return
	 *
	 */
	public String CountSuggest() {
		String wbid = "";
		long count = 0;
		int role = getRoleSign();
		db db = getdb().eq("state", 0);
		// 获取角色权限
		if (role == 6 || role == 5 || role == 4) {
			count = db.count();
		}
		if (role == 3 || role == 2) {
			wbid = (String) UserInfo.get("currentWeb");
		}
		if (!wbid.equals("")) {
			// if (!wbid.equals("594335af1a4769cbf5d04180")) {
			count = db.eq("wbid", wbid).count();
			// }else{
			// count = db.count();
			// }
		}
		return resultMessage(0, String.valueOf(count));
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
			try {
				if (IDCard.equals(UserInfo.get("IDCard").toString())) {
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
			} catch (Exception e) {
				nlogger.logout(e);
				string = resultMessage(99);
			}
		}
		return string;
	}

	/**
	 * 对已回复的咨询件进行评分
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param id
	 * @param score
	 * @return
	 *
	 */
	public String Score(String id, String score) {
		int role = getRoleSign();
		if (role == 6) {
			return resultMessage(7);
		}
		String result = resultMessage(99);
		JSONObject object = getdb().eq("_id", new ObjectId(id)).eq("state", 2).find();
		if (object != null) {
			try {
				if (!score.contains("score")) {
					score = "{\"score\":" + score + "}";
				}
				result = update(id, score);
			} catch (Exception e) {
				nlogger.logout(e);
				result = resultMessage(99);
			}
		}
		return result;
	}

	// 设置咨询件状态为公开
	public String setSelvel(String id) {
		int role = getRoleSign();
		if (role == 6) {
			return resultMessage(7);
		}
		String[] value = getId(id);
		int code = 99;
		String condString = "{\"slevel\":0}";
		try {
			if (value.length == 1) {
				code = getdb().eq("_id", new ObjectId(id)).data(condString).update() != null ? 0 : 99;
			} else {
				db db = getdb();
				for (String _id : value) {
					db.eq("_id", new ObjectId(_id));
				}
				code = db.data(condString).updateAll() == value.length ? 0 : 99;
			}
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return resultMessage(code, "咨询件公开设置成功");
	}

	// 显示某个企业下某个用户的所有咨询件信息
	public String showByUser(int ids, int pagesize) {
		long totalSize = 0, total = 0;
		JSONArray array = null;
		db db = getdb();
		try {
			if (UserInfo != null && UserInfo.size() != 0) {
				JSONObject obj = (JSONObject) UserInfo.get("_id");
				String userid = (String) obj.get("$oid");
				// String currentweb = (String) UserInfo.get("currentWeb");
				db.eq("userid", userid);
				array = db.dirty().field("_id,content,time,state,replyTime,replyContent").dirty().desc("time").page(ids,
						pagesize);
				totalSize = db.dirty().pageMax(pagesize);
				total = db.count();
				db.clear();
			}
		} catch (Exception e) {
			nlogger.logout(e);
		}
		return pageShow(array, ids, pagesize, total, totalSize);
	}

	public String FindByID(String id) {
		JSONObject object = getdb().eq("_id", new ObjectId(id))
				.field("_id,userid,name,consult,content,state,replyContent,score").find();
		// return resultMessage(decode(getSuggestImg(object)));
		return resultMessage(decode(object));
	}

	public String search(int idx, int pageSize, String condString) {
		JSONArray array = null;
		long totalSize = 0, total = 0;
		db db = getdb();
		JSONArray condArray = JSONArray.toJSONArray(condString);
		if (condArray != null && condArray.size() != 0) {
			db.where(condArray);
			array = db.dirty().page(idx, pageSize);
			totalSize = db.dirty().pageMax(pageSize);
			total = db.count();
		}
		return pageShow(array, idx, pageSize, total, totalSize);
	}

	/**
	 * 分页显示格式
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param array
	 * @param idx
	 * @param pageSize
	 * @param total
	 * @param totalSize
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	private String pageShow(JSONArray array, int idx, int pageSize, long total, long totalSize) {
		JSONObject obj = new JSONObject();
		if (array != null) {
			array = getImg(decode(array));
		} else {
			array = new JSONArray();
		}
		obj.put("data", array);
		obj.put("totalSize", totalSize);
		obj.put("pageSize", pageSize);
		obj.put("currentPage", idx);
		obj.put("total", total);
		return resultMessage(obj);
	}

	private String[] getId(String id) {
		List<String> list = new ArrayList<String>();
		JSONObject object;
		String[] value = id.split(",");
		for (String _id : value) {
			object = getdb().eq("_id", new ObjectId(_id)).field("slevel").find();
			if (object != null) {
				String slevel = object.get("slevel").toString();
				if (slevel.contains("$numberLong")) {
					slevel = JSONHelper.string2json(slevel).get("$numberLong").toString();
				}
				if (!("0").equals(slevel)) {
					list.add(_id);
				}
			}
		}
		return StringHelper.join(list).split(",");
	}

	private HashMap<String, Object> def() {
		String name = "";
		JSONObject object;
		String _id = "";
		if (UserInfo != null && UserInfo.size() != 0) {
			name = (String) UserInfo.get("name");
			object = (JSONObject) UserInfo.get("_id");
			_id = object.get("$oid").toString();
		}
		map.put("ownid", appsProxy.appid());
		map.put("name", name);
		map.put("userid", _id);
		map.put("consult", "");
		map.put("attr", ""); // 附件
		// map.put("attrVideo", ""); // 视频附件
		map.put("mode", 0);
		map.put("state", 0);// 0：已提交，1：已受理，2：已回复
		map.put("time", TimeHelper.nowMillis());
		map.put("replyContent", ""); // 回复内容
		map.put("replyTime", 0); // 回复时间
		map.put("reviewstate", 0); // 审核回复状态
		map.put("reviewContent", ""); // 审核回复 内容
		map.put("reviewtime", 0); // 审核回复数据的时间
		map.put("score", 0); // 评分
		map.put("wbid", ""); // 所属企业id
		map.put("slevel", 1); // 是否公开 0：表示公开，默认为不公开
		return map;
	}

	private String add(JSONObject object) {
		String result = resultMessage(99);
		int mode = Integer.parseInt(object.get("mode").toString());
		try {
			if (mode == 1) {
				// 实名验证,发送短信验证码
				result = RealName(object);
			} else {
				object.escapeHtmlPut("content", object.getString("content"));
				result = insert(object.toString());
			}
		} catch (Exception e) {
			nlogger.logout(e);
			result = resultMessage(99);
		}
		return result;
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
		int code = 0;
		// 发送验证码
		String ckcode = getValidateCode();
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
		return (float) getdb().count();
	}

	private float getCount(JSONObject obj) {
		db db = getdb();
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
		if (SID != null) {
			try {
				privilige privil = new privilige(SID);
				int roleplv = privil.getRolePV(appsProxy.appidString());
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
				if (roleplv >= 10000 && roleplv < 12000) {
					roleSign = 5; // 总管理员
				}
				if (roleplv >= 12000) {
					roleSign = 6; // jw
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
		JSONArray array2 = null;
		if (array != null) {
			if (array.size() == 0) {
				return array;
			}
			array2 = new JSONArray();
			JSONObject object = new JSONObject();
			for (Object obj : array) {
				object = (JSONObject) obj;
				object = decode(object);
				array2.add(object);
			}
		}
		return array2;
	}

	@SuppressWarnings("unchecked")
	private JSONObject decode(JSONObject object) {
		String content = "";
		if (object.containsKey("content") && object.get("content") != null) {
			content = (String) object.escapeHtmlGet("content");
			object.put("content", content);
		}
		if (object.containsKey("replyContent") && object.get("replyContent") != null) {
			content = (String) object.escapeHtmlGet("replyContent");
			object.put("replyContent", content);
		}
		if (object.containsKey("reviewContent") && object.get("reviewContent") != null) {
			content = (String) object.escapeHtmlGet("reviewContent");
			object.put("reviewContent", content);
		}
		return object;
	}

	private JSONArray getImg(JSONArray array) {
		String fileInfo = "";
		String tempid;
		JSONObject object;
		String fid = "";
		if (array != null) {
			try {
				if (array.size() == 0) {
					return array;
				}
				int l = array.size();
				for (int i = 0, len = l; i < len; i++) {
					object = (JSONObject) array.get(i);
					if (object.containsKey("attr") && !("").equals(object.get("attr").toString())) {
						tempid = object.getString("attr");
						if (ObjectId.isValid(tempid)) {
							fid += tempid + ",";
						}
					}
				}
				if (fid.length() > 1) {
					fid = StringHelper.fixString(fid, ',');
				}
				if (!fid.equals("")) {
					fileInfo = appsProxy.proxyCall("/GrapeFile/Files/getFiles/" + fid, null, "").toString();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return getFile(array, fileInfo);
	}

	@SuppressWarnings("unchecked")
	private JSONArray getFile(JSONArray array, String fileInfo) {
		JSONObject fileObj = JSONObject.toJSON(fileInfo);
		JSONObject object;
		if (array == null || array.size() == 0) {
			return null;
		}
		int l = array.size();
		if (fileObj != null) {
			for (int i = 0, len = l; i < len; i++) {
				object = (JSONObject) array.get(i);
				object = FillData(object, fileObj);
				array.set(i, object);
			}
		}
		return array;
	}

	@SuppressWarnings("unchecked")
	private JSONObject FillData(JSONObject object, JSONObject FileObj) {
		imgList = new ArrayList<String>();
		videoList = new ArrayList<String>();
		String attrlist = "", filetype = "";
		String[] attr;
		JSONObject FileInfoObj;
		attr = object.getString("attr").split(",");
		int attrlength = attr.length;
		if (attrlength != 0 && !attr[0].equals("") || attrlength > 1) {
			for (int j = 0; j < attrlength; j++) {
				FileInfoObj = (JSONObject) FileObj.get(attr[j]);
				if (FileInfoObj != null && FileInfoObj.size() != 0) {
					attrlist = getFile(1) + FileInfoObj.get("filepath").toString();
					filetype = FileInfoObj.get("filetype").toString();
					object.put("attrFile" + j, FileInfoObj);
					if ("1".equals(filetype)) {
						imgList.add(attrlist);
					}
					if ("2".equals(filetype)) {
						videoList.add(attrlist);
					}
				}
			}
		}
		object.put("image", imgList.size() != 0 ? StringHelper.join(imgList) : "");
		object.put("video", videoList.size() != 0 ? StringHelper.join(videoList) : "");
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
			nlogger.logout(e);
			value = "";
		}
		return value;
	}

	/**
	 * 获取文件url[内网url或者外网url]，0表示内网，1表示外网
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
	private String getFile(int signal) {
		String host = null;
		try {
			if (signal == 0 || signal == 1) {
				host = getAppIp("file").split("/")[signal];
			}
		} catch (Exception e) {
			nlogger.logout(e);
			host = null;
		}
		return "http://" + host;
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
			msg = "必填项不能为空";
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
		case 7:
			msg = "没有该操作权限";
			break;
		default:
			msg = "其他操作异常";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
