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
package com.datastax.driver.mapping;

import com.datastax.driver.core.*;
import com.datastax.driver.core.Cluster.Builder;
import com.datastax.driver.core.policies.ConstantReconnectionPolicy;
import com.datastax.driver.core.policies.DefaultRetryPolicy;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.utils.UUIDs;
import com.datastax.driver.mapping.entity.*;
import com.datastax.driver.mapping.meta.EntityFieldMetaData;
import com.datastax.driver.mapping.meta.EntityTypeMetadata;
import com.datastax.driver.mapping.option.WriteOptions;
import com.datastax.driver.mapping.schemasync.SyncOptionTypes;
import com.datastax.driver.mapping.schemasync.SyncOptions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.junit.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static org.junit.Assert.*;

public class MappingSessionTest {

	static Cluster cluster;
	static Session session;
	static String keyspace = "unittest";
	
	private MappingSession target;
	
	@BeforeClass 
	public static void init() { 
		String node = "127.0.0.1";
		Builder builder = Cluster.builder();
		builder.addContactPoint(node);
		builder.withLoadBalancingPolicy(LatencyAwarePolicy.builder(new RoundRobinPolicy()).build());
		builder.withReconnectionPolicy(new ConstantReconnectionPolicy(1000L));
		cluster = builder.build();
		session = cluster.connect();
		
		Cache<String, PreparedStatement> cache = CacheBuilder
			    .newBuilder()
			    .expireAfterAccess(1, TimeUnit.MILLISECONDS)
			    .maximumSize(1)
			    .concurrencyLevel(1)
			    .build();

		MappingSession.setStatementCache(cache);
	}

	@AfterClass 
	public static void clean() { 
		try {
			session.execute("DROP KEYSPACE IF EXISTS "+ keyspace);
			session.close();
			cluster.close();
		} catch (Exception e) {
			System.out.println(e);
		}
	}	
	
	@Before
	public void setUp() {
		session.execute("CREATE KEYSPACE IF NOT EXISTS "+ keyspace +" WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }");
		session.execute("USE "+keyspace);
		target = new MappingSession(keyspace, session);
	}
	
	@After
	public void cleanUp() {
		session.execute("DROP KEYSPACE IF EXISTS "+ keyspace);
		EntityTypeParser.removeAll();
	}

	@Test
	public void defaultConstructorTest() throws Exception {
		UUID uuid = UUID.randomUUID();
		EntityWithIndexes obj = new EntityWithIndexes();
		obj.setCount(100);
		obj.setEmail("email@at");
		obj.setName("test");
		obj.setTimeStamp(new Date());
		obj.setUuid(uuid);
		obj.setLongstamp((new Date()).getTime());
		
		MappingSession m = new MappingSession();
		m.setKeyspace(keyspace);
		m.setSession(session);
		EntityWithIndexes loaded = m.get(EntityWithIndexes.class, uuid);
		assertNull(loaded);
		
		m.save(obj);
		loaded = m.get(EntityWithIndexes.class, uuid);
		assertEquals(obj, loaded);
		
		m.delete(loaded);
		loaded = m.get(EntityWithIndexes.class, uuid);
		assertNull(loaded);
	}
	
	@Test
	public void saveAndGetAndDeleteTest() throws Exception {
		UUID uuid = UUID.randomUUID();
		EntityWithIndexes obj = new EntityWithIndexes();
		obj.setCount(100);
		obj.setEmail("email@at");
		obj.setName("test");
		obj.setTimeStamp(new Date());
		obj.setUuid(uuid);
		obj.setLongstamp((new Date()).getTime());
		
		EntityWithIndexes loaded = target.get(EntityWithIndexes.class, uuid);
		assertNull(loaded);
		
		target.save(obj);
		loaded = target.get(EntityWithIndexes.class, uuid);
		assertEquals(obj, loaded);
		
		target.delete(loaded);
		loaded = target.get(EntityWithIndexes.class, uuid);
		assertNull(loaded);
	}

	@Test
	public void doNotSyncTest() throws Exception {
		MappingSession msession = new MappingSession(keyspace, session, SyncOptions.withOptions().doNotSync());
		try {
			msession.get(Simple.class, UUID.randomUUID());
		} catch (Exception e) {
			 assertTrue(e.getMessage().contains("unconfigured columnfamily simple"));
			 return;
		}
		assertTrue(false);
	}
	
	@Test
	public void saveAndGetWithOptionsTest() throws Exception {
		UUID uuid = UUID.randomUUID();
		Simple obj = new Simple();
		obj.setTimestamp(new Date());
		obj.setAge(55).setId(uuid);
		
		Simple loaded = target.get(Simple.class, uuid);
		assertNull(loaded);
		WriteOptions so = new WriteOptions()
			.setTtl(3)
			.setTimestamp(42)
			.setConsistencyLevel(ConsistencyLevel.ANY)
			.setRetryPolicy(DefaultRetryPolicy.INSTANCE);
		target.save(obj, so);
		loaded = target.get(Simple.class, uuid);
		assertEquals(obj, loaded);
		
		Thread.sleep(3000);
		loaded = target.get(Simple.class, uuid);
		assertNull(loaded);
	}

	@Test
	public void saveAndGetWithDefaultTtlTest() throws Exception {
		UUID uuid = UUID.randomUUID();
		EntityWithTtl obj = new EntityWithTtl();
		obj.setTimestamp(new Date());
		obj.setId(uuid);
		
		EntityWithTtl loaded = target.get(EntityWithTtl.class, uuid);
		assertNull(loaded);
		target.save(obj);
		loaded = target.get(EntityWithTtl.class, uuid);
		assertNotNull(loaded);
		
		Thread.sleep(4000);
		loaded = target.get(EntityWithTtl.class, uuid);
		assertNull(loaded);
	}

	@Test
	public void saveAndGetWithOverrideTtlTest() throws Exception {
		UUID uuid = UUID.randomUUID();
		EntityWithTtl obj = new EntityWithTtl();
		obj.setTimestamp(new Date());
		obj.setId(uuid);
		
		EntityWithTtl loaded = target.get(EntityWithTtl.class, uuid);
		assertNull(loaded);
		
		target.save(obj, new WriteOptions().setTtl(10));
		
		// ttl is 10 sec. obj still should be alive
		Thread.sleep(5000);
		loaded = target.get(EntityWithTtl.class, uuid);
		assertNotNull(loaded);

		// 10 sec passed, obj should expire
		Thread.sleep(5000);
		loaded = target.get(EntityWithTtl.class, uuid);
		assertNull(loaded);
	}
	
	@Test
	public void getByQueryTest() throws Exception {
		for (int i = 0; i < 3; i++) {
			EntityWithIndexes obj = new EntityWithIndexes();
			obj.setCount(100);
			obj.setEmail("email@test");
			obj.setName("test"+i);
			obj.setTimeStamp(new Date());
			obj.setUuid(UUID.randomUUID());
			target.save(obj);
		}
		EntityTypeMetadata emeta = EntityTypeParser.getEntityMetadata(EntityWithIndexes.class);
		EntityFieldMetaData fdata = emeta.getFieldMetadata("email");
		
		Statement query = QueryBuilder.select().all().from(keyspace, emeta.getTableName()).where(eq(fdata.getColumnName(), "email@test"));
		List<EntityWithIndexes> items = target.getByQuery(EntityWithIndexes.class, query);
		assertEquals(3, items.size());
	}

	@Test
	public void getByQueryStringTest() throws Exception {
		for (int i = 0; i < 3; i++) {
			EntityWithIndexes obj = new EntityWithIndexes();
			obj.setCount(100);
			obj.setEmail("email@test");
			obj.setName("test"+i);
			obj.setTimeStamp(new Date());
			obj.setUuid(UUID.randomUUID());
			target.save(obj);
		}
		
		String query = "SELECT * FROM test_entity_index WHERE email='email@test'";
		List<EntityWithIndexes> items = target.getByQuery(EntityWithIndexes.class, query);
		assertEquals(3, items.size());
	}
	
	@Test
	public void testCollections() throws Exception {
		EntityWithCollections obj = new EntityWithCollections();
		
		UUID uuid = UUID.randomUUID();
		obj.setId(uuid);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, uuid);
		
		Map<String, BigDecimal> map = new HashMap<String, BigDecimal>();
		map.put("key1", new BigDecimal(100.55));
		map.put("key1", new BigDecimal(100.55555));
		map.put("key1", new BigDecimal(101.5500000333));
		obj.setRates(map);
		
		List<Integer> list = new ArrayList<Integer>();
		list.add(100);
		list.add(200);
		list.add(300);
		obj.setTrades(list);
		
		Set<String> set = new HashSet<String>();
		set.add("100");
		set.add("200");
		set.add("300");
		obj.setRefs(set);

		target.save(obj);
		loaded = target.get(EntityWithCollections.class, uuid);
		
		assertEquals(obj, loaded);		
	}
	
	@Test
	public void testCollectionsOverride() throws Exception {
		EntityWithCollectionsOverride obj = new EntityWithCollectionsOverride();
		
		UUID uuid = UUID.randomUUID();
		obj.setId(uuid);
		target.save(obj);
		
		EntityWithCollectionsOverride loaded = target.get(EntityWithCollectionsOverride.class, uuid);
		
		Map<String, BigDecimal> map = new HashMap<String, BigDecimal>();
		map.put("key1", new BigDecimal(100.55));
		map.put("key1", new BigDecimal(100.55555));
		map.put("key1", new BigDecimal(101.5500000333));
		obj.setRates(map);
		
		List<Integer> list = new ArrayList<Integer>();
		list.add(100);
		list.add(200);
		list.add(300);
		obj.setTrades(list);
		
		Set<String> set = new HashSet<String>();
		set.add("100");
		set.add("200");
		set.add("300");
		obj.setRefs(set);

		target.save(obj);
		loaded = target.get(EntityWithCollectionsOverride.class, uuid);
		
		assertTrue(loaded.getRates() instanceof TreeMap);	
		assertTrue(loaded.getRefs() instanceof TreeSet);
		assertTrue(loaded.getTrades() instanceof LinkedList);
		
	}
	
	@Test
	public void saveAndGetAndDeleteWithCompoundCompositeKeyTest() throws Exception {
		SimpleKey partition = new SimpleKey();
		partition.setName("name");
		partition.setRank(10);
		partition.setT1(UUIDs.timeBased());
		partition.setT2(UUIDs.timeBased());		
		
		CompositeKey key = new CompositeKey();
		key.setKey(partition);
		
		Date created = new Date();
		key.setCreated(created);
		key.setEmail("email@at");
		
		EntityWithCompositeKey obj = new EntityWithCompositeKey();
		obj.setKey(key);
		obj.setTimestamp(1000); 
		obj.setAsof(created);
		
		EntityWithCompositeKey loaded = target.get(EntityWithCompositeKey.class, key);
		assertNull(loaded);
		
		target.save(obj);
		loaded = target.get(EntityWithCompositeKey.class, key);
		assertEquals(obj, loaded);
		
		target.delete(loaded);
		loaded = target.get(EntityWithCompositeKey.class, key);
		assertNull(loaded);
	}

	@Test
	public void saveAndGetAndDeleteWithCompoundClusteringKeyTest() throws Exception {
		
		ClusteringKey ckey = new ClusteringKey();
		ckey.setExpense_id(100);
		ckey.setUser("test");
		Date created = new Date();
		
		PrimaryKey key = new PrimaryKey();
		key.setPk(ckey);
		
		EntityWithClusteringKey obj = new EntityWithClusteringKey();
		obj.setKey(key);
		obj.setTimestamp(1000); 
		obj.setAsof(created);
		
		EntityWithClusteringKey loaded = target.get(EntityWithClusteringKey.class, key);
		assertNull(loaded);
		
		target.save(obj);
		loaded = target.get(EntityWithClusteringKey.class, key);
		assertEquals(obj, loaded);
		
		target.delete(loaded);
		loaded = target.get(EntityWithClusteringKey.class, key);
		assertNull(loaded);
	}

	@Test
	public void saveAndGetAndDeleteWithSimpleCompositeKeyTest() throws Exception {
		SimpleKey key = new SimpleKey();
		key.setName("name");
		key.setRank(10);
		key.setT1(UUIDs.timeBased());
		key.setT2(UUIDs.timeBased());
		
		Date created = new Date();
		
		EntityWithKey obj = new EntityWithKey();
		obj.setKey(key);
		obj.setTimestamp(1000); 
		obj.setAsof(created);
		
		EntityWithKey loaded = target.get(EntityWithKey.class, key);
		assertNull(loaded);
		
		target.save(obj);
		loaded = target.get(EntityWithKey.class, key);
		assertEquals(obj, loaded);
		
		target.delete(loaded);
		loaded = target.get(EntityWithKey.class, key);
		assertNull(loaded);
	}
	
	@Test
	public void saveAndGetAndDeleteMixedCaseTest() throws Exception {
		int id = 12245;
		EntityMixedCase obj = new EntityMixedCase();
		obj.setId(id);
		obj.setFirstName("firstName"); 
		obj.setLastName("lastName");
		obj.setAge(25);
		
		EntityMixedCase loaded = target.get(EntityMixedCase.class, id);
		assertNull(loaded);
		
		target.save(obj);
		loaded = target.get(EntityMixedCase.class, id);
		assertEquals(obj, loaded);
		
		target.delete(loaded);
		loaded = target.get(EntityMixedCase.class, id);
		assertNull(loaded);
	}
	
	@Test
	public void entityWithVersionTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithVersion obj = new EntityWithVersion();
		obj.setId(id);
		obj.setName("ver1"); 
		
		EntityWithVersion loaded = target.get(EntityWithVersion.class, id);
		assertNull(loaded);
		
		// save object ver1 
		EntityWithVersion saved = target.save(obj);
		
		// get object ver1
		EntityWithVersion obj1 = target.get(EntityWithVersion.class, id);
		assertEquals(obj1, saved);
		assertEquals(1, saved.getVersion());
		
		// save object ver2
		saved = target.save(saved);
		EntityWithVersion obj2 = target.get(EntityWithVersion.class, id);
		assertEquals(obj2, saved);
		assertEquals(2, saved.getVersion());		
		
		saved = target.save(obj1);
		assertNull(saved);
	}	
	
	@Test
	public void appendToListTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		List<Integer> trades = new ArrayList<Integer>();
		trades.add(1);
		trades.add(2);
		obj.setTrades(trades);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(2, loaded.getTrades().size());
		
		target.append(id, EntityWithCollections.class, "trades", 3);
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getTrades().contains(3));
		assertEquals(new Integer(3), loaded.getTrades().get(2));
	}

	@Test
	public void appendAtTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		List<Integer> trades = new ArrayList<Integer>();
		trades.add(100);
		trades.add(200);
		trades.add(300);
		obj.setTrades(trades);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(3, loaded.getTrades().size());
		
		target.replaceAt(id, EntityWithCollections.class, "trades", 3, 0);
		sleep(100);
		loaded = target.get(EntityWithCollections.class, id);
		assertEquals(new Integer(3), loaded.getTrades().get(0));
		assertEquals(3, loaded.getTrades().size());
		
		target.replaceAt(id, EntityWithCollections.class, "trades", 33, 2);
		loaded = target.get(EntityWithCollections.class, id);
		assertEquals(new Integer(33), loaded.getTrades().get(2));
		assertEquals(3, loaded.getTrades().size());
		
		target.replaceAt(id, EntityWithCollections.class, "trades", 22, 1);
		loaded = target.get(EntityWithCollections.class, id);
		assertEquals(new Integer(22), loaded.getTrades().get(1));	
		assertEquals(3, loaded.getTrades().size());
	}
	
	@Test
	public void appendAllToListTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		List<Integer> trades = new ArrayList<Integer>();
		trades.add(1);
		trades.add(2);
		obj.setTrades(trades);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(2, loaded.getTrades().size());
		
		List<Integer> adds = new ArrayList<Integer>();
		adds.add(5);
		adds.add(6);
		target.append(id, EntityWithCollections.class, "trades", adds);
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getTrades().contains(5));
		assertEquals(4, loaded.getTrades().size());
	}	

	@Test
	public void prependTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		List<Integer> trades = new ArrayList<Integer>();
		trades.add(1);
		trades.add(2);
		obj.setTrades(trades);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(2, loaded.getTrades().size());
		
		List<Integer> adds = new ArrayList<Integer>();
		adds.add(5);
		adds.add(6);
		target.prepend(id, EntityWithCollections.class, "trades", adds);
		loaded = target.get(EntityWithCollections.class, id);
		assertEquals(new Integer(6), loaded.getTrades().get(0));
		assertEquals(new Integer(5), loaded.getTrades().get(1));
		assertEquals(new Integer(1), loaded.getTrades().get(2));
		assertEquals(new Integer(2), loaded.getTrades().get(3));

		target.prepend(id, EntityWithCollections.class, "trades", 10);
		loaded = target.get(EntityWithCollections.class, id);
		assertEquals(new Integer(10), loaded.getTrades().get(0));
	}
	
	@Test
	public void appendAllToSetTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		Set<String> refs = new HashSet<String>();
		refs.add("100");
		refs.add("abc");
		obj.setRefs(refs);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(2, loaded.getRefs().size());
		
		Set<String> adds = new HashSet<String>();
		adds.add("fgdsfgdsfgd");
		adds.add("200");
		
		target.append(id, EntityWithCollections.class, "refs", adds);
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getRefs().contains("fgdsfgdsfgd"));
		assertEquals(4, loaded.getRefs().size());
	}	
	
	@Test
	public void appendToSetTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		Set<String> refs = new HashSet<String>();
		refs.add("100");
		refs.add("abc");
		obj.setRefs(refs);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(2, loaded.getRefs().size());
		
		target.append(id, EntityWithCollections.class, "refs", "56545sd4");
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getRefs().contains("56545sd4"));
		assertEquals(3, loaded.getRefs().size());
	}	
	
	@Test
	public void appendToMapTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		Map<String, BigDecimal> rates = new HashMap<String, BigDecimal>();
		rates.put("abc", new BigDecimal(100));
		rates.put("cde", new BigDecimal(10000.554154));
		obj.setRates(rates);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(2, loaded.getRates().size());
		
		Map<String, BigDecimal> add = new HashMap<String, BigDecimal>();
		add.put("bcd", new BigDecimal(0.000005555));
		target.append(id, EntityWithCollections.class, "rates", add);
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getRates().containsKey("bcd"));
		assertEquals(new BigDecimal(0.000005555), loaded.getRates().get("bcd"));
		assertEquals(3, loaded.getRates().size());
	}	
	
	@Test
	public void deleteTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		Map<String, BigDecimal> rates = new HashMap<String, BigDecimal>();
		rates.put("abc", new BigDecimal(100));
		rates.put("cde", new BigDecimal(10000.554154));
		obj.setRates(rates);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(2, loaded.getRates().size());
	
		target.deleteValue(id, EntityWithCollections.class, "rates");
		loaded = target.get(EntityWithCollections.class, id);
		assertEquals(0, loaded.getRates().size());
	}
	
	
	@Test
	public void appendWithOptionsTest() throws Exception {
		WriteOptions wo = new WriteOptions().setTtl(3);
		
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		Set<String> refs = new HashSet<String>();
		refs.add("100");
		refs.add("abc");
		obj.setRefs(refs);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(2, loaded.getRefs().size());
		
		target.append(id, EntityWithCollections.class, "refs", "56545sd4", wo);
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getRefs().contains("56545sd4"));
		assertEquals(3, loaded.getRefs().size());
		
		sleep(3000);
		
		loaded = target.get(EntityWithCollections.class, id);
		assertEquals(2, loaded.getRefs().size());
		assertFalse(loaded.getRefs().contains("56545sd4"));
	}

	@Test
	public void batchTest() throws Exception {
		
		UUID uuid1 = UUID.randomUUID();
		Simple obj1 = new Simple();
		obj1.setTimestamp(new Date());
		obj1.setId(uuid1);

		UUID uuid2 = UUID.randomUUID();
		Simple obj2 = new Simple();
		obj2.setTimestamp(new Date());
		obj2.setId(uuid2);
		target.save(obj2);
		
		target.withBatch()
			.save(obj1)
			.delete(obj2)
			.execute();
		
		Simple loaded1 = target.get(Simple.class, uuid1);
		Simple loaded2 = target.get(Simple.class, uuid2);
		assertNull(loaded2);
		assertNotNull(loaded1);
	}

	@Test
	public void batchTtlTest() throws Exception {
		
		UUID uuid1 = UUID.randomUUID();
		Simple obj1 = new Simple();
		obj1.setTimestamp(new Date());
		obj1.setId(uuid1);

		UUID uuid2 = UUID.randomUUID();
		EntityWithTtl obj2 = new EntityWithTtl();
		obj2.setTimestamp(new Date());
		obj2.setId(uuid2);

		UUID uuid3 = UUID.randomUUID();
		EntityWithTtl obj3 = new EntityWithTtl();
		obj3.setTimestamp(new Date());
		obj3.setId(uuid3);
		
		target.withBatch()
			.save(obj1)
			.save(obj2)
			.save(obj3, new WriteOptions().setTtl(10))
			.execute();
		
		Simple loaded1 = target.get(Simple.class, uuid1);
		EntityWithTtl loaded2 = target.get(EntityWithTtl.class, uuid2);
		EntityWithTtl loaded3 = target.get(EntityWithTtl.class, uuid3);
		
		assertNotNull(loaded1);
		assertNotNull(loaded2);
		assertNotNull(loaded3);
		
		Thread.sleep(5000);
		loaded1 = target.get(Simple.class, uuid1);
		loaded2 = target.get(EntityWithTtl.class, uuid2);
		loaded3 = target.get(EntityWithTtl.class, uuid3);		
		assertNotNull(loaded1);
		assertNull(loaded2);
		assertNotNull(loaded3);

		Thread.sleep(5000);
		loaded1 = target.get(Simple.class, uuid1);
		loaded2 = target.get(EntityWithTtl.class, uuid2);
		loaded3 = target.get(EntityWithTtl.class, uuid3);		
		assertNotNull(loaded1);
		assertNull(loaded2);
		assertNull(loaded3);
		
		target.delete(loaded1);
	}
	
	@Test
	public void addToSetTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(0, loaded.getRefs().size());
		
		loaded.addRef("200");
		target.save(loaded);
		
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getRefs().contains("200"));
		assertEquals(1, loaded.getRefs().size());
		
		loaded.addRef("300");
		target.save(loaded);
		
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getRefs().contains("300"));
		assertEquals(2, loaded.getRefs().size());		
	}
	
	@Test
	public void addToListTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(0, loaded.getTrades().size());
		
		loaded.addTrade(200);
		target.save(loaded);
		
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getTrades().contains(200));
		assertEquals(1, loaded.getTrades().size());
		
		loaded.addTrade(300);
		target.save(loaded);
		
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getTrades().contains(300));
		assertEquals(2, loaded.getTrades().size());
	}
	
	@Test
	public void addToMapTest() throws Exception {
		UUID id = UUID.randomUUID();
		EntityWithCollections obj = new EntityWithCollections();		
		obj.setId(id);
		target.save(obj);
		
		EntityWithCollections loaded = target.get(EntityWithCollections.class, id);
		assertEquals(0, loaded.getRates().size());
		
		loaded.addRate("200", new BigDecimal("100"));
		target.save(loaded);
		
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getRates().containsKey("200"));
		assertEquals(1, loaded.getRates().size());
		
		loaded.addRate("300", new BigDecimal("300"));
		target.save(loaded);
		
		loaded = target.get(EntityWithCollections.class, id);
		assertTrue(loaded.getRates().containsKey("300"));
		assertEquals(2, loaded.getRates().size());		
	}
	
	private void sleep(long n) {
		try {
			Thread.sleep(n);
		} catch (Exception e) {}
	}
	
	@Test
	public void saveEntityWithEnumTest() throws Exception {
		UUID uuid = UUID.randomUUID();
		EntityWithEnum obj = new EntityWithEnum();
		obj.setId(uuid);
		obj.setMonth(Month.JUNE);
		
		EntityWithEnum loaded = target.get(EntityWithEnum.class, uuid);
		assertNull(loaded);
		
		target.save(obj);
		loaded = target.get(EntityWithEnum.class, uuid);
		assertEquals(obj, loaded);
		
		obj.setMonth(Month.APRIL);
		target.save(obj);
		loaded = target.get(EntityWithEnum.class, uuid);
		assertEquals(obj, loaded);
		
		target.delete(loaded);
		loaded = target.get(EntityWithEnum.class, uuid);
		assertNull(loaded);
	}
	
	@Test
	public void saveEntityWithStringEnumTest() throws Exception {
		UUID uuid = UUID.randomUUID();
		EntityWithStringEnum obj = new EntityWithStringEnum();
		obj.setId(uuid);
		obj.setPage(Page.GITHUB);
		
		EntityWithStringEnum loaded = target.get(EntityWithStringEnum.class, uuid);
		assertNull(loaded);
		
		target.save(obj);
		loaded = target.get(EntityWithStringEnum.class, uuid);
		assertEquals(obj, loaded);
		
		obj.setPage(Page.CASSANDRA);
		target.save(obj);
		loaded = target.get(EntityWithStringEnum.class, uuid);
		assertEquals(obj, loaded);
		
		target.delete(loaded);
		loaded = target.get(EntityWithStringEnum.class, uuid);
		assertNull(loaded);
	}

	@Test
	public void updateIndividualPropertyTest() throws Exception {
		UUID uuid = UUID.randomUUID();
		Simple obj = new Simple();
		obj.setName("myName");
		obj.setAge(55);
		obj.setId(uuid);
		target.save(obj);
		
		target.updateValue(uuid, Simple.class, "name", "yourName");
		target.updateValue(uuid, Simple.class, "age", 25);
		Simple loaded =  target.get(Simple.class, uuid);
		assertEquals(25, loaded.getAge());
		assertEquals("yourName", loaded.getName());
		
		EntityWithEnum eobj = new EntityWithEnum();
		eobj.setId(uuid);
		eobj.setMonth(Month.JUNE);	
		target.save(eobj);
		
		target.updateValue(uuid, EntityWithEnum.class, "month", Month.MAY);
		EntityWithEnum eloaded = target.get(EntityWithEnum.class, uuid);
		assertEquals(Month.MAY, eloaded.getMonth());
		
		//String-Enum
		EntityWithStringEnum seobj = new EntityWithStringEnum();
		seobj.setId(uuid);
		seobj.setPage(Page.DATASTAX);	
		target.save(seobj);
		
		target.updateValue(uuid, EntityWithStringEnum.class, "page", Page.CASSANDRA.toString());
		EntityWithStringEnum seloaded = target.get(EntityWithStringEnum.class, uuid);
		assertEquals(Page.CASSANDRA, Page.getPage(seloaded.getPage()));
	}
	
	@Test
	public void any2anyTest() throws Exception {
		Date d = new Date();
		UUID uuid = UUID.randomUUID();
		Simple obj = new Simple();
		obj.setTimestamp(d);
		obj.setName("myName");
		obj.setAge(55);
		obj.setId(uuid);
		obj.setRandom(20);
		obj.setVersion(1);
		
		target.save(obj);
		
		ResultSet rs = session.execute("SELECT name, age, timestamp FROM simple");	
		List<Any> result = target.getFromResultSet(Any.class, rs);
		Any a = result.get(0);
		assertEquals(obj.getAge(),a.getAge());
		assertEquals(obj.getTimestamp(),a.getTimestamp());
		assertEquals(obj.getName(),a.getName());
		
		target.delete(obj);
	}

    @Test
    public void ableToGetCounterField() throws Exception {
        // setup
        target.maybeSync(EntityWithCounter.class);
        String query = "UPDATE EntityWithCounter SET counterValue = counterValue + 1 WHERE source = 'testSource'";
        session.execute(query);
        session.execute(query);

        // call sut
        EntityWithCounter entityWithCounter = target.get(EntityWithCounter.class, "testSource");

        // check results
        assertEquals("testSource", entityWithCounter.getSource());
        assertEquals(2, entityWithCounter.getCounterValue());
    }
    
    @Test
    public void entityWithStaticTest() throws Exception {
        target.maybeSync(EntityWithStaticField.class);
        
        ClusteringKey k1 = new ClusteringKey();
        k1.setUser("test");
        k1.setExpense_id(1);
        EntityWithStaticField e1 = new EntityWithStaticField();
        e1.setKey(k1);
        e1.setBalance(100);
        e1.setPaid(false);
        target.save(e1);
        sleep(1000);

        ClusteringKey k2 = new ClusteringKey();
        k2.setUser("test");
        k2.setExpense_id(2);
        EntityWithStaticField e2 = new EntityWithStaticField();
        e2.setKey(k2);
        e2.setBalance(0);
        e2.setPaid(true);
        target.save(e2); 
        
        e1 = target.get(EntityWithStaticField.class, k1);
        e2 = target.get(EntityWithStaticField.class, k2);
        
        assertEquals(0, e1.getBalance());
        assertEquals(e1.getBalance(), e2.getBalance());
        assertNotSame(e1.getPaid(), e2.getPaid());
    }
    
    @Test
    public void getFromResultSetTest() throws Exception {
        Simple obj1 = new Simple();
        obj1.setTimestamp(new Date());
        obj1.setName("myName");
        obj1.setAge(55);
        obj1.setId(UUID.randomUUID());
        obj1.setRandom(20);
        obj1.setVersion(1);
        target.save(obj1);
        
        Simple obj2 = new Simple();
        obj2.setTimestamp(new Date());
        obj2.setName("hisName");
        obj2.setAge(55);
        obj2.setId(UUID.randomUUID());
        obj2.setRandom(20);
        obj2.setVersion(1);
        target.save(obj2);
        
        ResultSet rs = session.execute("SELECT * FROM simple");  
        List<Simple> result = target.getFromResultSet(Simple.class, rs);
        assertEquals(2,result.size());
        
        target.delete(obj1);
        target.delete(obj2);
    }    
    
    @Test
    public void getFromRowTest() throws Exception {
        Simple obj1 = buildSimpleEntity();
        target.save(obj1);
        
        Simple obj2 = buildSimpleEntity();
        target.save(obj2);
        
        ResultSet rs = session.execute("SELECT * FROM simple");  
        Iterator<Row> it = rs.iterator();
        while (it.hasNext()) {
            Simple obj = target.getFromRow(Simple.class, it.next());
            assertNotNull(obj);
        }
        
        target.delete(obj1);
        target.delete(obj2);
    }     

    @Test
    public void getFromRowsTest() throws Exception {
        Simple obj1 = buildSimpleEntity();
        target.save(obj1);
        
        Simple obj2 = buildSimpleEntity();
        target.save(obj2);
        
        ResultSet rs = session.execute("SELECT * FROM simple");  
        List<Row> rows = rs.all();
        List<Simple> result = target.getFromRows(Simple.class, rows); 
        assertEquals(2,result.size());
        
        target.delete(obj1);
        target.delete(obj2);
    }     
    
    private Simple buildSimpleEntity() {
        Simple obj = new Simple();
        obj.setTimestamp(new Date());
        obj.setName("myName");
        obj.setAge(55);
        obj.setId(UUID.randomUUID());
        obj.setRandom(20);
        obj.setVersion(1);
        return obj;
    }

    @Test
    public void updateSelectedPropertiesTest() throws Exception {
        UUID uuid = UUID.randomUUID();
        Simple obj = new Simple();
        obj.setName("myName");
        obj.setAge(55);
        obj.setId(uuid);
        target.save(obj);
        sleep(100);
        String[] props = {"name", "age"};
        Object[] vals = {"yourName", 25};
        target.updateValues(uuid, Simple.class, props, vals);
        sleep(100);
        Simple loaded =  target.get(Simple.class, uuid);
        assertEquals(25, loaded.getAge());
        assertEquals("yourName", loaded.getName());
    }    
    
    @Test
    public void autoSetUUIDTest() throws Exception {
        Simple obj = new Simple();
        obj.setName("myName");
        obj.setAge(55);
        target.save(obj);
    }
    
    @Test
    public void autoSetTimeUUIDTest() throws Exception {
        EntityWithTimeUUID obj = new EntityWithTimeUUID();
        obj.setName("myName");
        obj.setKey(new TimeUUIDKey());
        target.save(obj);
    }

    @Test
    public void whenAnFieldAddedToTable_thenPreparedSelectShouldReturnTheField() throws Exception {
        // setup
        UUID userId = UUID.randomUUID();
        UserWithAge user = new UserWithAge(userId, "user1", 100);
        assertNotNull(target.save(user));
        target.setSyncOptions(SyncOptions.withOptions().add(SyncOptionTypes.DoNotSync));

        // call sut
        target.get(UserWithoutAge.class, userId);
        UserWithAge withAge = target.get(UserWithAge.class, userId);

        // check results
        assertEquals(100, withAge.getAge());
        assertEquals(userId, withAge.getId());
    }
    
    @Test
    public void autoBoxedSettersGettersTest() throws Exception {
        UUID id = UUID.randomUUID();
        EntityAutoBoxed e = new EntityAutoBoxed();
        e.setId(id);
        e.setAge(10);
        e.setBalance(100.896);
        e.setIsGood(true);
        target.save(e);

        e = target.get(EntityAutoBoxed.class, id);
        e = target.get(EntityAutoBoxed.class, id);
        e = target.get(EntityAutoBoxed.class, id);
        e = target.get(EntityAutoBoxed.class, id);
        
        assertEquals(Integer.valueOf(10), e.getAge());
        assertEquals(true, e.getIsGood());
        assertEquals(Double.valueOf(100.896), Double.valueOf(e.getBalance()));
    }
}
