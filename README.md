Phrase Count
============

An example application that computes phrase counts for unique documents using
Accismus. Each new unique document that is added causes phrase counts to be
incremented. Unique documents have reference counts based on the number of
locations that point to them.  When a unique document is no longer referenced
by any location, then the phrase counts will be decremented appropriately.  

After cloning this repo, build with following command.  May need to install
Accismus into your local maven repo first.

```
mvn package 
```

Running Mini Instance
---------------------

If you do not have Accumulo, Hadoop, Zookeeper, and Accismus setup, then you
can start an MiniAccismus instance with the following command.  This command
will create an `accismus.properties` that can be used by the following command
in this example.

```
mvn exec:java -Dexec.mainClass=phrasecount.cmd.Mini -Dexec.args="/tmp/mac accismus.properties" -Dexec.classpathScope=test 
```

After the mini command prints out `Wrote : <accismus.properties>` then its ready to use. 


Adding documents
----------------

The following command will scan the directory `$TXT_DIR` looking for .txt files to add.  The scan is recursive.  

```
mvn exec:java -Dexec.mainClass=phrasecount.cmd.Load -Dexec.args="accismus.properties $TXT_DIR" -Dexec.classpathScope=test
```

Printing phrases
----------------

After documents were added, the following command will printout phrase counts.
Try modifying a document you added and running the load command again, you
should eventually see the phrase counts change.

```
mvn exec:java -Dexec.mainClass=phrasecount.cmd.Print -Dexec.args="accismus.properties" -Dexec.classpathScope=test
```

The print command will print out the number of unique documents and the number
of processed documents.  If the number of processed documents is less than the
number of unique documents, then there is still work to do.  After the load
command runs, the documents will have been added or updated.  However the
phrase counts will not update until the Observer runs in the background. 


Deploying example
-----------------

The following instructions cover running this example on an installed Accismus
instance. Copy this jar to the Accismus observer directory.

```
cp target/phrasecount-0.0.1-SNAPSHOT.jar $ACCISMUS_HOME/lib/observers
```

Modify `$ACCISMUS_HOME/conf/initialization.properties` and replace the observer
lines with the following:

```
accismus.worker.observer.0=index,check,,phrasecount.DocumentIndexer
```

Now initialize and start Accismus as outlined in its docs.  The following shows

Generating data
---------------

Need some data? Use `links` to generate text files from web pages.

```
mkdir data
links -dump 1 -no-numbering -no-references http://accumulo.apache.org > data/accumulo.txt
links -dump 1 -no-numbering -no-references http://hadoop.apache.org > data/hadoop.txt
links -dump 1 -no-numbering -no-references http://zookeeper.apache.org > data/zookeeper.txt
```
