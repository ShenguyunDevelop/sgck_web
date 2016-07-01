package com.sgck.web.support;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.sgck.core.platform.SubSystemGather;

@Component
public class DefaultSubSystemGather implements SubSystemGather {
	private Map<String,ApplicationContext> subSystemMap = new ConcurrentHashMap<String,ApplicationContext>();
	@Override
	public Set<String> getSubSystemNames() {
		return subSystemMap.keySet();
	}

	@Override
	public Object getSubSystemContext(String systemName) {
		return subSystemMap.get(systemName);
	}

	@Override
	public void addSubSystem(String name, Object context) {
		if(!(context instanceof ApplicationContext)){
			return;
		}
		
		subSystemMap.put(name, (ApplicationContext)context);
	}
}
