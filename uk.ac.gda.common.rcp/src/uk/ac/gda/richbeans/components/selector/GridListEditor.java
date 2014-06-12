/*-
 * Copyright © 2009 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.gda.richbeans.components.selector;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import uk.ac.gda.richbeans.components.EventManagerDelegate;

import com.swtdesigner.SWTResourceManager;

/**
 * A list editor that edits the beans with a user interface.
 * 
 * Note that the beans in the bean list should have
 * hashCode() and equals() implemented correctly.
 * 
 * Not designed to be extended in general. Instead use
 * setEditorUI(...) to provide a composite template for
 * editing each item.
 * 
 * The items are in a fixed grid and there are no add/remove buttons.
 * 
 * @author fcp94556
  *
 */
public final class GridListEditor extends ListEditor {

	public enum GRID_ORDER {
		/**
		 * Default
		 */
		LEFT_TO_RIGHT_TOP_TO_BOTTOM,
		/**
		 * 
		 */
		TOP_TO_BOTTOM_RIGHT_TO_LEFT,
		/**
		 * 
		 */
		CUSTOM_MAP
	}
	
	protected TableViewer tableViewer;
    protected int selectedIndex;
    protected final int columns,rows;
	private Listener tableListener;
	private Map<Integer,Integer> gridMap;
	protected ColumnLabelProvider columnLabelProviderDelegate;
	private Collection<TableViewerColumn> tableColumns;
	private int gridWidth;
	private int gridHeight;
	private GRID_ORDER gridOrder;
	
    /**
     * Creates a 10 by 10 grid.
     * @param par
     */
	public GridListEditor(final Composite par, final int switches) {
		this(par,switches,5,5);
		setGridHeight(126);
		setGridWidth(130);
	}
	
	/**
	 * @param par
	 * @param switches
	 * @param squareTotal
	 */
	public GridListEditor(final Composite par, final int switches, final int squareTotal) {
		this(par,switches,getSide(squareTotal, squareTotal),getSide(squareTotal, 1));
	}
	
	private static int getSide(int squareTotal,int alternative) {
		if (squareTotal>3)
		    return (int)Math.pow(squareTotal, 0.5);
		return alternative;
	}

	/**
 	 * @param par
 	 * @param switches
 	 * @param columns
 	 * @param rows
 	 */
	public GridListEditor(final Composite par, final int switches, final int columns, final int rows) {
		super(par, switches, GridListEditor.class.getName());
 		eventDelegate = new EventManagerDelegate(this);
 		this.columns = columns;
 		this.rows = rows;
		setLayout(new GridLayout(1, false));
		
		if ((rows*columns)>1) {
			tableViewer = new TableViewer(this, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION | SWT.NO_SCROLL);
			tableViewer.getTable().setLinesVisible(true);
			createContentProvider();
			createLabelProvider();
			createEditor();
			tableListener = new Listener() {
		 		@Override
		 		public void handleEvent(Event event) {
		 			tableViewer.setSelection(null);
		 		}
			};
			tableViewer.getTable().addListener(SWT.Selection,tableListener);
			tableViewer.getControl().setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			setGridHeight(100); // Default
			tableViewer.setInput(new Object());
		} 
		else
			selectedIndex = 0; // And stays that way.

		// Must set this rather than subclassing (if do not set to null default is this).
		editorUI = null;
	}
	
	@Override
	public StructuredViewer getViewer() {
		return tableViewer;
	}
	
	@Override
	public void dispose() {
		if (tableViewer!=null&&tableViewer.getTable()!=null&&!tableViewer.getTable().isDisposed())
		    tableViewer.getTable().removeListener(SWT.Selection, tableListener);
		super.dispose();
	}
	
	@Override
	protected BeanWrapper getSelectedBeanWrapper() {
		return beans.get(selectedIndex);
	}
	
	@Override
	public int getSelectedIndex() {
		return selectedIndex;
	}
	
	@Override
	public void setSelectedIndex(int selectedIndex) {
		this.selectedIndex = selectedIndex;
	}

	@Override
	protected void setSelectedBean(BeanWrapper wrapper, boolean fireListeners) {
		super.setSelectedBean(wrapper, fireListeners);
		this.selectedIndex = beans.indexOf(wrapper);
		if (tableViewer != null)
			tableViewer.refresh();
	}
	
	@Override
	public void setValue(Object value) {
		final List<?> obs = (List<?>)value;
		this.clear();
		for (int i = 0; i < obs.size(); i++) {
			final Object bean = obs.get(i);
			final BeanWrapper wrapper = new BeanWrapper(bean);
			wrapper.setName(i+"");
			beans.add(wrapper);
		}
		if (!beans.isEmpty()) {
			selectedIndex = 0;
			setSelectedBean(beans.get(0), false);
		}
		if (tableViewer!=null) 
			tableViewer.refresh();
		updateEditingUIVisibility();
		notifyValueListeners();
	}
	
	private void createEditor() {
		if (tableViewer==null) return;
		final String [] cols = new String[columns];
		for (int i = 0; i < cols.length; i++) 
			cols[i]=i+"";
		tableViewer.setColumnProperties(cols);
		// Just used to detect click position.
		tableViewer.setCellModifier(new ICellModifier() {
			@Override
			public boolean canModify(Object element, String property) {
				return updateElement(element, property);
			}
			@Override
			public Object getValue(Object element, String property) {
				return null;
			}
			@Override
			public void modify(Object item, String property, Object value) {
			}
		});
	}	

	public boolean updateElement(Object element, String property){
		if (!GridListEditor.this.isOn()) 
			return false;
		final Integer row = (Integer)element;
		final int col = Integer.parseInt(property);
		selectedIndex = getElementIndex(row, col);
		final BeanWrapper bean = beans.get(selectedIndex);
		setSelectedBean(bean, false);
		tableViewer.refresh();
		if (listeners!=null) {
			final BeanSelectionEvent evt = new BeanSelectionEvent(this, selectedIndex, bean.getBean());
			for (BeanSelectionListener l : listeners) 
				l.selectionChanged(evt);
		}
		return false;
	}
	
	private void createContentProvider() {
		if (tableViewer==null) 
			return;
		final Integer [] vals = new Integer[rows];
		for (int i = 0; i < vals.length; i++) 
			vals[i] = i;
		tableViewer.setContentProvider(new IStructuredContentProvider() {
			
			@Override
			public void dispose() {}
			
			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}
			
			@Override
			public Object[] getElements(Object inputElement) {
			    return vals;
			}
			
		});
	}

	public void setAdditionalLabelProvider(ColumnLabelProvider prov) {
		this.columnLabelProviderDelegate = prov;
	}
	
	public void setColumnWidth(final int width) {
		if (tableViewer==null)
			return;
		for (TableViewerColumn col : tableColumns)
			col.getColumn().setWidth(width);
	}
	
	private void createLabelProvider() {
		if (tableViewer==null) 
			return;
		tableColumns = new HashSet<TableViewerColumn>(7);
		ColumnViewerToolTipSupport.enableFor(tableViewer,ToolTip.NO_RECREATE);
		int width = Math.round(((float)this.gridWidth)/((float)columns));
		if (width<1) 
			width = 25;
		for (int i = 0; i < columns; i++) {
			final int column = i;
			final TableViewerColumn col = new TableViewerColumn(tableViewer, SWT.NONE);
			col.getColumn().setWidth(width);
			tableColumns.add(col);
			col.setLabelProvider(new ColumnLabelProvider() {
				@Override
				public String getText(Object element) {
					if (columnLabelProviderDelegate!=null) {
						final int elementIndex= getElementIndex(element, column);
						if(beans.size()>0){
							final String text = columnLabelProviderDelegate.getText(beans.get(elementIndex).getBean());
							if (text!=null) 
								return text;
						}
					}
					if (!(element instanceof Integer)) 
						return null;
					int index = getElementIndex(element, column);
					return index+"";
				}
				private final Color blue = SWTResourceManager.getColor(SWT.COLOR_DARK_BLUE);
				private final Color white = SWTResourceManager.getColor(SWT.COLOR_WHITE);
				@Override
				public Color getBackground(Object element) {
					if (columnLabelProviderDelegate!=null) {
						if(beans.size()>0){
							BeanWrapper beanWrapper = beans.get(getElementIndex(element, column));
							Object bean = beanWrapper.getBean();
							final Color color = columnLabelProviderDelegate.getBackground(bean);
							if (color!=null)
								return color;
						}
					}
					if (getElementIndex(element, column) == selectedIndex)
						return blue;
					return null;
				}
				@Override
				public Color getForeground(Object element) {
					if (columnLabelProviderDelegate!=null) {
						if(beans.size()>0){
							BeanWrapper beanWrapper = beans.get(getElementIndex(element, column));
							Object bean = beanWrapper.getBean();
							final Color color = columnLabelProviderDelegate.getForeground(bean);
							if (color!=null)
								return color;
						}
					}
					if (getElementIndex(element, column) == selectedIndex)
						return white;						
					return null;
				}
				
				@Override
				public String getToolTipText(Object element) {
					return "Click to select element.";
				}

				@Override
				public Point getToolTipShift(Object object) {
					return new Point(5, 5);
				}

				@Override
				public int getToolTipDisplayDelayTime(Object object) {
					return 50;
				}

				@Override
				public int getToolTipTimeDisplayed(Object object) {
					return 10000;
				}
				
			});
		}
	}
	
	public GRID_ORDER getGridOrder() {
		return gridOrder;
	}

	public void setGridOrder(GRID_ORDER order) {
		this.gridOrder = order;
	}
	
	/**
	 * This method takes the zero based row and column and
	 * gives a zero based index in the array. The formula used
	 * is one of the GRID_ORDER types.
	 * 
	 * columns==rows for this to work
	 * 
	 * @param element
	 * @param col
	 * @return zero based index for the array of items.
	 */
	private int getElementIndex(Object element, int col) {
		final int row = ((Integer)element).intValue();
		if (gridOrder==GRID_ORDER.CUSTOM_MAP) {
			final int index = (col)+(row*columns);
			return gridMap.get(index);
		}
		if (columns==rows) {
			// Assume left to right top to buttom
			if (gridOrder==null||gridOrder==GRID_ORDER.LEFT_TO_RIGHT_TOP_TO_BOTTOM)
				return (col)+(row*columns);
			final int ref = (rows*columns)-columns;
			return (ref+row)-(col*columns);
		}
		return (row*columns)+col;
	}
	
	public int getElementIndex(Object element, int col, GRID_ORDER gridOrder, int columns, int rows, Map<Integer, Integer> gridMap) {
		final int row = ((Integer)element).intValue();
		if (gridOrder==GRID_ORDER.CUSTOM_MAP) {
			final int index = (col)+(row*columns);
			return gridMap.get(index);
		}
		if (columns==rows) {
			// Assume left to right top to buttom
			if (gridOrder==null||gridOrder==GRID_ORDER.LEFT_TO_RIGHT_TOP_TO_BOTTOM)
				return (col)+(row*columns);
			final int ref = (rows*columns)-columns;
			return (ref+row)-(col*columns);
		}
		return (row*columns)+col;
	}
	
	public int getGridHeight() {
		return gridHeight;
	}

	public void setGridHeight(int listHeight) {
		if (tableViewer==null) return;
		this.gridHeight = listHeight;
		final GridData data = (GridData)tableViewer.getControl().getLayoutData();
		data.heightHint = listHeight;
	}
	
	public int getGridWidth() {
		return gridWidth;
	}

	public void setGridWidth(int gridWidth) {
		if (tableViewer==null) return;
		this.gridWidth = gridWidth;
		GridData data = (GridData)tableViewer.getControl().getLayoutData();
		data.widthHint = gridWidth;
		int width = Math.round(((float)this.gridWidth)/((float)columns));
		if (width<1)
			width = 25;
		setColumnWidth(width);
		gridHeight = rows*25;
		data = (GridData)tableViewer.getControl().getLayoutData();
		data.heightHint = gridHeight;
	}

	@Override
	protected void updateButtons() {
		// Intentionally does nothing
	}

	public Map<Integer, Integer> getGridMap() {
		return gridMap;
	}

	public void setGridMap(Map<Integer, Integer> gridMap) {
		this.gridMap = gridMap;
	}

	public TableViewer getTableViewer() {
		return tableViewer;
	}

	public int getColumns() {
		return columns;
	}

	public int getRows() {
		return rows;
	}

}