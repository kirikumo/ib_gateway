package com.avalok.ib.rm;

import java.util.LinkedHashMap;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

/** Risk JSON. Serialize a Map with WriteMapNullValue so null fields stay in the string. */
public final class RmJson {
	private RmJson() {}

	public static String toJson(Map<String, ?> map) {
		return JSON.toJSONString(map, SerializerFeature.WriteMapNullValue);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parse(String json) {
		if (json == null || json.isEmpty()) return new LinkedHashMap<>();
		Map<String, Object> map = JSON.parseObject(json, LinkedHashMap.class);
		return map == null ? new LinkedHashMap<>() : map;
	}
}
