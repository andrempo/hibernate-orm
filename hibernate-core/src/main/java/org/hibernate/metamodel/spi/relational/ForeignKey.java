/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010 by Red Hat Inc and/or its affiliates or by
 * third-party contributors as indicated by either @author tags or express
 * copyright attribution statements applied by the authors.  All
 * third-party contributions are distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.spi.relational;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.hibernate.AssertionFailure;
import org.hibernate.MappingException;
import org.hibernate.dialect.Dialect;
import org.jboss.logging.Logger;

/**
 * Models the notion of a foreign key.
 * <p/>
 * Note that this need not mean a physical foreign key; we just mean a relationship between 2 table
 * specifications.
 *
 * @author Gavin King
 * @author Steve Ebersole
 * @author Brett Meyer
 */
public class ForeignKey extends AbstractConstraint {
	
    private static final Logger LOG = Logger.getLogger( ForeignKey.class );

	private static final String ON_DELETE = " on delete ";
	private static final String ON_UPDATE = " on update ";

	private final TableSpecification targetTable;
	private List<Column> targetColumns;

	private ReferentialAction deleteRule = ReferentialAction.NO_ACTION;
	private ReferentialAction updateRule = ReferentialAction.NO_ACTION;

	protected ForeignKey(TableSpecification sourceTable, TableSpecification targetTable, String name) {
		super( sourceTable, name );
		if ( targetTable == null ) {
			throw new IllegalArgumentException( "targetTable must be non-null." );
		}
		this.targetTable = targetTable;
	}

	protected ForeignKey(TableSpecification sourceTable, TableSpecification targetTable) {
		this( sourceTable, targetTable, null );
	}

	public TableSpecification getSourceTable() {
		return getTable();
	}

	public TableSpecification getTargetTable() {
		return targetTable;
	}

	public List<Column> getSourceColumns() {
		return getColumns();
	}

	public List<Column> getTargetColumns() {
		return targetColumns == null
				? getTargetTable().getPrimaryKey().getColumns()
				: Collections.unmodifiableList( targetColumns );
	}

	protected int generateConstraintColumnListId() {
		return 31 * super.generateConstraintColumnListId() + targetTable.generateColumnListId( getTargetColumns() );
	}

	@Override
	public void addColumn(Column column) {
		addColumnMapping( column, null );
	}

	public void addColumnMapping(Column sourceColumn, Column targetColumn) {
		if ( targetColumn == null ) {
			if ( targetColumns != null ) {
				LOG.debugf(
						"Attempt to map column [%s] to no target column after explicit target column(s) named for FK [name=%s]",
						sourceColumn.toLoggableString(),
						getName()
				);
			}
		}
		else {
			checkTargetTable( targetColumn );
			if ( targetColumns == null ) {
				if (!internalColumnAccess().isEmpty()) {
					LOG.debugf(
							"Value mapping mismatch as part of FK [table=%s, name=%s] while adding source column [%s]",
							getTable().toLoggableString(),
							getName(),
							sourceColumn.toLoggableString()
					);
				}
				targetColumns = new ArrayList<Column>();
			}
			targetColumns.add( targetColumn );
		}
		internalAddColumn( sourceColumn );
	}

	private void checkTargetTable(Column targetColumn) {
		if ( !getTargetTable().hasValue( targetColumn ) ) {
			throw new AssertionFailure(
					String.format(
							"Unable to add column to constraint; target column [%s] is not in target table [%s]",
							targetColumn.toLoggableString(),
							getTargetTable().toLoggableString()
					)
			);
		}
	}

	public ReferentialAction getDeleteRule() {
		return deleteRule;
	}

	public void setDeleteRule(ReferentialAction deleteRule) {
		this.deleteRule = deleteRule;
	}

	public ReferentialAction getUpdateRule() {
		return updateRule;
	}

	public void setUpdateRule(ReferentialAction updateRule) {
		this.updateRule = updateRule;
	}

	@Override
	public String[] sqlDropStrings(Dialect dialect) {
		final StringBuilder buf = new StringBuilder( "alter table " );
		buf.append( getTable().getQualifiedName( dialect ) );
		buf.append( dialect.getDropForeignKeyString() );
		if ( dialect.supportsIfExistsBeforeConstraintName() ) {
			buf.append( "if exists " );
		}
		buf.append( getName() );
		if ( dialect.supportsIfExistsAfterConstraintName() ) {
			buf.append( " if exists" );
		}
		return new String[] { buf.toString() };
	}

	public String sqlConstraintStringInAlterTable(Dialect dialect) {
		String[] columnNames = new String[ getColumnSpan() ];
		String[] targetColumnNames = new String[ getColumnSpan() ];
		int i=0;
		Iterator<Column> itTargetColumn = getTargetColumns().iterator();
		for ( Column column : getColumns() ) {
			if ( ! itTargetColumn.hasNext() ) {
				throw new MappingException( "More constraint columns that foreign key target columns." );
			}
			columnNames[i] = column.getColumnName().getText( dialect );
			targetColumnNames[i] = ( itTargetColumn.next() ).getColumnName().getText( dialect );
			i++;
		}
		if ( itTargetColumn.hasNext() ) {
			throw new MappingException( "More foreign key target columns than constraint columns." );
		}
		StringBuilder sb =
				new StringBuilder(
						dialect.getAddForeignKeyConstraintString(
								getName(),
								columnNames,
								targetTable.getQualifiedName( dialect ),
								targetColumnNames,
								this.targetColumns == null ||
										this.targetColumns.equals( targetTable.getPrimaryKey().getColumns() )
						)
				);
		// TODO: If a dialect does not support cascade-delete, can it support other actions? (HHH-6428)
		// For now, assume not.
		if ( dialect.supportsCascadeDelete() ) {
			if ( deleteRule != ReferentialAction.NO_ACTION ) {
				sb.append( ON_DELETE ).append( deleteRule.getActionString() );
			}
			if ( updateRule != ReferentialAction.NO_ACTION ) {
				sb.append( ON_UPDATE ).append( updateRule.getActionString() );
			}
		}
		return sb.toString();
	}

	public static enum ReferentialAction {
		NO_ACTION( "no action" ),
		CASCADE( "cascade" ),
		SET_NULL( "set null" ),
		SET_DEFAULT( "set default" ),
		RESTRICT( "restrict" );

		private final String actionString;

		private ReferentialAction(String actionString) {
			this.actionString = actionString;
		}

		public String getActionString() {
			return actionString;
		}
	}

	public class ColumnMapping {
		private final int position;

		public ColumnMapping(int position) {
			this.position = position;
		}

		public Column getSourceColumn() {
			return getColumns().get(  position );
		}

		public Column getTargetColumn() {
			return getTargetColumns().get( position );
		}
	}

	public boolean referencesPrimaryKey() {
		return targetColumns == null
				|| targetColumns.equals( targetTable.getPrimaryKey().getColumns() );
	}

	public Iterable<ColumnMapping> getColumnMappings() {
		final List<Column> targetColumns = getTargetColumns();
		if ( getColumns().size() != targetColumns.size() ) {
			// todo : this needs to be an error, though not sure the best type yet
		}
		final List<ColumnMapping> columnMappingList = new ArrayList<ColumnMapping>();
		for ( int i = 0; i < getColumns().size(); i++ ) {
			columnMappingList.add( new ColumnMapping( i ) );
		}
		return columnMappingList;
	}
	
	@Override
	public String getExportIdentifier() {
		return getSourceTable().getLoggableValueQualifier() + ".FK-" + getName();
	}
}
