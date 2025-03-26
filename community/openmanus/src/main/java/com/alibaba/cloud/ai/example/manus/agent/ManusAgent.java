/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.example.manus.agent;

import com.alibaba.cloud.ai.example.manus.llm.LlmService;
import com.alibaba.cloud.ai.example.manus.service.ChromeDriverService;

import org.springframework.ai.model.tool.ToolCallingManager;

public class ManusAgent extends BrowserAgent {

	;

	public ManusAgent(LlmService llmService, ToolCallingManager toolCallingManager,
			ChromeDriverService chromeDriverService) {
		super(llmService, toolCallingManager, chromeDriverService);
	}

	@Override
	public String getName() {
		return "Manus";
	}

	@Override
	public String getDescription() {
		return "A versatile agent that can solve various tasks using multiple tools";
	}

}
