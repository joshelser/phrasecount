package phrasecount;

import static phrasecount.Constants.EXPORT_DOC_COUNT_COL;
import static phrasecount.Constants.EXPORT_SUM_COL;
import static phrasecount.Constants.STAT_CHECK_COL;
import static phrasecount.Constants.TYPEL;
import io.fluo.api.config.ScannerConfiguration;
import io.fluo.api.data.Bytes;
import io.fluo.api.data.Column;
import io.fluo.api.data.Span;
import io.fluo.api.iterator.ColumnIterator;
import io.fluo.api.iterator.RowIterator;
import io.fluo.api.observer.AbstractObserver;

import java.util.Map.Entry;

import io.fluo.api.types.TypedTransactionBase;

import io.fluo.api.client.TransactionBase;

/**
 * This Observer processes high cardinality phrases. It sums up all of the random stat columns that were set.
 */

public class HCCounter extends AbstractObserver {

  @Override
  public void process(TransactionBase tx, Bytes row, Column col) throws Exception {
    TypedTransactionBase ttx = TYPEL.wrap(tx);

    ScannerConfiguration scanConfig = new ScannerConfiguration();
    scanConfig.setSpan(Span.prefix(row.toString(), "stat", "sum:"));

    int sum = sumAndDelete(tx, tx.get(scanConfig));
    int newSum = ttx.get().row(row).col(Constants.STAT_SUM_COL).toInteger(0) + sum;
    ttx.mutate().row(row).col(Constants.STAT_SUM_COL).set(newSum);

    scanConfig.setSpan(Span.prefix(row.toString(), "stat", "docCount:"));
    int docCount = sumAndDelete(tx, tx.get(scanConfig));
    int newDocCount = ttx.get().row(row).col(Constants.STAT_DOC_COUNT_COL).toInteger(0) + docCount;
    ttx.mutate().row(row).col(Constants.STAT_DOC_COUNT_COL).set(newDocCount);

    if (ttx.get().row(row).col(Constants.EXPORT_SUM_COL).toInteger() == null) {
      ttx.mutate().row(row).col(EXPORT_SUM_COL).set(newSum);
      ttx.mutate().row(row).col(EXPORT_DOC_COUNT_COL).set(newDocCount);
    }

    ttx.mutate().row(row).col(Constants.EXPORT_CHECK_COL).set();
  }

  private int sumAndDelete(TransactionBase tx, RowIterator rowIterator) {
    int sum = 0;
    while (rowIterator.hasNext()) {
      Entry<Bytes,ColumnIterator> rEntry = rowIterator.next();
      ColumnIterator citer = rEntry.getValue();
      while (citer.hasNext()) {
        Entry<Column,Bytes> cEntry = citer.next();
        sum += Integer.parseInt(cEntry.getValue().toString());
        tx.delete(rEntry.getKey(), cEntry.getKey());
      }
    }

    return sum;
  }

  @Override
  public ObservedColumn getObservedColumn() {
    return new ObservedColumn(STAT_CHECK_COL, NotificationType.WEAK);
  }

}
