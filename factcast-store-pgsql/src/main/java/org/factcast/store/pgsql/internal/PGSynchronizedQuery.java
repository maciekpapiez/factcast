package org.factcast.store.pgsql.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * executes a query in a synchronized fashion, to make sure, results are
 * processed in order as well as sequentially.
 * 
 * Note, that you can hint the query method if index usage is wanted. In a
 * catchup scenario, you will probably want to use an index. If however you are
 * following a fact stream and expect to get a low number of rows (if any) back
 * from the query because you seek for the "latest" changes, it is way more
 * efficient to scan the table. In that case call <code>query(false)</code>.
 * 
 * DO NOT use an instance as a singleton/Spring bean. This class is meant be
 * instantiated by each subscription.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@RequiredArgsConstructor
class PGSynchronizedQuery {

    private JdbcTemplate jdbcTemplate;

    private String sql;

    private PreparedStatementSetter setter;

    private RowCallbackHandler rowHandler;

    private TransactionTemplate transactionTemplate;

    PGSynchronizedQuery(@NonNull JdbcTemplate jdbcTemplate, @NonNull String sql,
            @NonNull PreparedStatementSetter setter, @NonNull RowCallbackHandler rowHandler) {
        this.jdbcTemplate = jdbcTemplate;
        this.sql = sql;
        this.setter = setter;
        this.rowHandler = rowHandler;

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(
                jdbcTemplate.getDataSource());
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // the synchronized here is crucial!
    public synchronized void run(boolean useIndex) {
        if (useIndex) {
            jdbcTemplate.query(sql, setter, rowHandler);
        } else {
            transactionTemplate.execute(new TransactionCallback<Object>() {
                @Override
                public Object doInTransaction(TransactionStatus status) {

                    jdbcTemplate.execute("SET LOCAL enable_bitmapscan=0;");
                    jdbcTemplate.query(sql, setter, rowHandler);
                    return null;
                }
            });
        }
    }
}