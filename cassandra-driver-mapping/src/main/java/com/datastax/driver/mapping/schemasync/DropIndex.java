/*
 *      Copyright (C) 2014 Eugene Valchkou.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.datastax.driver.mapping.schemasync;

import java.nio.ByteBuffer;
import java.util.Map;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.RegularStatement;

public class DropIndex extends RegularStatement {
	
	private static final String DROP_INDEX_TEMPLATE_CQL = "DROP INDEX IF EXISTS %s;";
	
	final String keyspace;
	final String indexName;

	DropIndex(String keyspace, String indexName) {
		this.keyspace = keyspace;
		this.indexName = indexName;
	}

	@Override
	public String getQueryString() {		
		return String.format(DROP_INDEX_TEMPLATE_CQL, indexName);
	}

	@Override
	public String getKeyspace() {
		return keyspace;
	}

	@Override
	public boolean hasValues() {
		// TODO Auto-generated method stub
		return false;
	}

    @Override
    public ByteBuffer[] getValues(ProtocolVersion arg0, CodecRegistry codecRegistry) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean usesNamedValues() {
    	return false;
    }

    @Override
    public boolean hasValues(CodecRegistry codecRegistry) {
    	return false;
    }

    @Override
    public Map<String, ByteBuffer> getNamedValues(ProtocolVersion protocolVersion, CodecRegistry codecRegistry) {
    	return null;
    }

    @Override
    public String getQueryString(CodecRegistry codecRegistry) {
    	return null;
    }

    @Override
    public ByteBuffer getRoutingKey(ProtocolVersion protocolVersion, CodecRegistry codecRegistry) {
    	return null;
    }
}