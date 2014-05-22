package phrasecount;

import java.util.Collections;
import java.util.Map;

import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.apache.accumulo.minicluster.MiniAccumuloConfig;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import accismus.api.Admin;
import accismus.api.Column;
import accismus.api.LoaderExecutor;
import accismus.api.SnapshotFactory;
import accismus.api.config.InitializationProperties;
import accismus.api.config.LoaderExecutorProperties;
import accismus.api.test.MiniAccismus;
import accismus.api.types.StringEncoder;
import accismus.api.types.TypedSnapshot;
import accismus.api.types.TypedSnapshot.BytesDecoder;

import com.google.common.collect.Sets;

//TODO make this an integration test

public class DocumentIndexerTest {
  public static TemporaryFolder folder = new TemporaryFolder();
  public static MiniAccumuloCluster cluster;
  private static InitializationProperties props;
  private static MiniAccismus miniAccismus;
  private static final PasswordToken password = new PasswordToken("secret");

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    folder.create();
    MiniAccumuloConfig cfg = new MiniAccumuloConfig(folder.newFolder("miniAccumulo"), new String(password.getPassword()));
    cluster = new MiniAccumuloCluster(cfg);
    cluster.start();

    // TODO add helper code to make this shorter
    props = new InitializationProperties();
    props.setAccumuloInstance(cluster.getInstanceName());
    props.setAccumuloUser("root");
    props.setAccumuloPassword("secret");
    props.setZookeeperRoot("/accismus");
    props.setZookeepers(cluster.getZooKeepers());
    props.setAccumuloTable("data");
    props.setNumThreads(5);
    props.setObservers(Collections.singletonMap(new Column("index", "check"), DocumentIndexer.class.getName()));

    Admin.initialize(props);

    miniAccismus = new MiniAccismus(props);
    miniAccismus.start();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    miniAccismus.stop();
    cluster.stop();
    folder.delete();
  }
  
  static class PhraseInfo {
    public PhraseInfo() {}

    public PhraseInfo(int s, int n) {
      this.sum = s;
      this.numDocs = n;
    }
    int sum;
    int numDocs;

    public boolean equals(Object o) {
      if (o instanceof PhraseInfo) {
        PhraseInfo opi = (PhraseInfo) o;
        return sum == opi.sum && numDocs == opi.numDocs;
      }

      return false;
    }
  }

  private PhraseInfo getPhraseInfo(SnapshotFactory snapFact, String phrase) throws Exception {

    TypedSnapshot tsnap = new TypedSnapshot(snapFact.createSnapshot(), new StringEncoder());
    Map<Column,BytesDecoder> map = tsnap.getd("phrase:" + phrase, Sets.newHashSet(new Column("stat", "sum"), new Column("stat", "docCount")));

    PhraseInfo pi = new PhraseInfo();
    pi.sum = map.get(new Column("stat", "sum")).toInteger();
    pi.numDocs = map.get(new Column("stat", "docCount")).toInteger();

    return pi;
  }
  
  private void loadDocument(LoaderExecutor le, String uri, String content) {
    Document doc = new Document(uri, content);
    le.execute(new DocumentLoader(doc));
    miniAccismus.waitForObservers();
  }

  @Test
  public void test1() throws Exception {

    LoaderExecutorProperties lep = new LoaderExecutorProperties(props);
    lep.setNumThreads(0);
    lep.setQueueSize(0);
    
    LoaderExecutor le = new LoaderExecutor(lep);

    loadDocument(le, "/foo1", "This is only a test.  Do not panic. This is only a test.");

    SnapshotFactory snapFact = new SnapshotFactory(props);

    Assert.assertEquals(new PhraseInfo(2, 1), getPhraseInfo(snapFact, "is only a test"));
    Assert.assertEquals(new PhraseInfo(1, 1), getPhraseInfo(snapFact, "test do not panic"));

    // add new document w/ different content and overlapping phrase.. should change some counts
    loadDocument(le, "/foo2", "This is only a test");

    Assert.assertEquals(new PhraseInfo(3, 2), getPhraseInfo(snapFact, "is only a test"));
    Assert.assertEquals(new PhraseInfo(1, 1), getPhraseInfo(snapFact, "test do not panic"));

    // add new document w/ same content, should not change any counts
    loadDocument(le, "/foo3", "This is only a test");

    Assert.assertEquals(new PhraseInfo(3, 2), getPhraseInfo(snapFact, "is only a test"));
    Assert.assertEquals(new PhraseInfo(1, 1), getPhraseInfo(snapFact, "test do not panic"));

    // change the content of /foo1, should change counts
    loadDocument(le, "/foo1", "The test is over, for now.");

    Assert.assertEquals(new PhraseInfo(1, 1), getPhraseInfo(snapFact, "the test is over"));
    Assert.assertEquals(new PhraseInfo(1, 1), getPhraseInfo(snapFact, "is only a test"));
    Assert.assertEquals(new PhraseInfo(0, 0), getPhraseInfo(snapFact, "test do not panic"));

    // change content of foo2, should not change anything
    loadDocument(le, "/foo2", "The test is over, for now.");

    Assert.assertEquals(new PhraseInfo(1, 1), getPhraseInfo(snapFact, "the test is over"));
    Assert.assertEquals(new PhraseInfo(1, 1), getPhraseInfo(snapFact, "is only a test"));
    Assert.assertEquals(new PhraseInfo(0, 0), getPhraseInfo(snapFact, "test do not panic"));

    String oldHash = new Document("/foo3", "This is only a test").getHash();
    TypedSnapshot tsnap = new TypedSnapshot(snapFact.createSnapshot(), new StringEncoder());
    Assert.assertNotNull(tsnap.getd("doc:" + oldHash, new Column("doc", "content")).toString());
    Assert.assertEquals(1, tsnap.getd("doc:" + oldHash, new Column("doc", "refCount")).toInteger(0));

    // dereference document that foo3 was referencing
    loadDocument(le, "/foo3", "The test is over, for now.");

    Assert.assertEquals(new PhraseInfo(1, 1), getPhraseInfo(snapFact, "the test is over"));
    Assert.assertEquals(new PhraseInfo(0, 0), getPhraseInfo(snapFact, "is only a test"));
    Assert.assertEquals(new PhraseInfo(0, 0), getPhraseInfo(snapFact, "test do not panic"));

    tsnap = new TypedSnapshot(snapFact.createSnapshot(), new StringEncoder());
    Assert.assertNull(tsnap.getd("doc:" + oldHash, new Column("doc", "content")).toString());
    Assert.assertNull(tsnap.getd("doc:" + oldHash, new Column("doc", "refCount")).toInteger());

    le.shutdown();
    snapFact.close();

  }
}
  