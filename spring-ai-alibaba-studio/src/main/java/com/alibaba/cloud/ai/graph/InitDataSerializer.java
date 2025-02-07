/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.graph;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class InitDataSerializer extends StdSerializer<InitData> {

    public InitDataSerializer(Class<InitData> t) {
        super(t);
    }

    @Override
    public void serialize(InitData initData, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();

        jsonGenerator.writeStringField("graph", initData.graph());
        jsonGenerator.writeStringField("title", initData.title());
        jsonGenerator.writeObjectField("args", initData.args());

        // jsonGenerator.writeArrayFieldStart("nodes" );
        // for( var node : initData.nodes() ) {
        // jsonGenerator.writeString(node);
        // }
        // jsonGenerator.writeEndArray();

        jsonGenerator.writeArrayFieldStart("threads");
        for (var thread : initData.threads()) {
            jsonGenerator.writeStartArray();
            jsonGenerator.writeString(thread.id());
            jsonGenerator.writeStartArray(thread.entries());
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndArray();
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
    }

}
