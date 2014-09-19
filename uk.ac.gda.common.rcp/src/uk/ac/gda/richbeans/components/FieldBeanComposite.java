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

package uk.ac.gda.richbeans.components;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.gda.richbeans.ACTIVE_MODE;
import uk.ac.gda.richbeans.beans.BeanProvider;
import uk.ac.gda.richbeans.beans.BeanUI;
import uk.ac.gda.richbeans.beans.IExpressionManager;
import uk.ac.gda.richbeans.beans.IExpressionWidget;
import uk.ac.gda.richbeans.beans.IFieldCollection;
import uk.ac.gda.richbeans.beans.IFieldProvider;
import uk.ac.gda.richbeans.beans.IFieldWidget;
import uk.ac.gda.richbeans.beans.BeanUI.BeanProcessor;
import uk.ac.gda.richbeans.components.selector.BeanSelectionListener;
import uk.ac.gda.richbeans.editors.BeanExpressionManager;
import uk.ac.gda.richbeans.event.ValueAdapter;
import uk.ac.gda.richbeans.event.ValueEvent;
import uk.ac.gda.richbeans.event.ValueListener;

/**
 * Used to edit complex types in RichBeanEditors. Also subclasses
 * for editing lists of complex types.
 * 
 * You can either subclass this class directly for use with a complex
 * type or call the setEditorUI(...) with a composite containing the 
 * class to use as the editing UI.
 * 
 * You must call setEditorClass(...) as the template bean is required
 * since the editorBean is allowed to be null.
 * 
 * If subclassing you need to call addValueListeners() after setting
 * the setEditorClass or your values will not take.
 * 
 * @author Matthew Gerring
 *
 */
public class FieldBeanComposite extends FieldComposite implements IFieldCollection, IExpressionWidget, IFieldProvider, BeanProvider {
	private static Logger logger = LoggerFactory.getLogger(FieldBeanComposite.class);
	protected Object beanTemplate;
	protected Object editorUI;
	protected Object editorBean;
    protected String listenerName;
 	protected boolean active = true;// Important to start with true! For what reason??
	protected Collection<BeanSelectionListener> listeners;
	private boolean isEnabled = true;
	private boolean isOn = false;
	private ACTIVE_MODE activeMode = ACTIVE_MODE.SET_VISIBLE_AND_ACTIVE;
	
	/**
	 * @param parent
	 * @param style
	 */
	public FieldBeanComposite(Composite parent, int style) {
		this(parent, style, FieldBeanComposite.class.getName());
		editorUI = this;
	}
	
	/**
	 * Optionally name the listener which will be attached to the editorUI.
	 * This allows nested BeanListEditors etc to work. Otherwise the parent
	 * editor attaches in preference and the UI does not update correctly.
	 * 
	 * @param parent
	 * @param style
	 * @param listenerName
	 */
	public FieldBeanComposite(Composite parent, int style, final String listenerName) {
		super(parent,style);
		this.listenerName = listenerName;
		this.editorUI     = this;
	}

	/**
	 * Call this method to set the editing class from which a new instance will be
	 * generated and this used to synchronise bean editors.
	 * Failure to call this class before the setValue(...) methods are called,
	 * will result in exceptions for some classes extending this class.
	 * 
	 * If using this class directly the setValue(...) call sets the editorClass
	 * automatically. There is no need to call this method then.
	 * 
	 * @param clazz 
	 * 
	 * 
	 */
	public void setEditorClass(final Class<?> clazz) {
		try {
			beanTemplate = clazz.newInstance();
			if (editorUI==this) 
				addValueListeners(this);
		} catch (Exception e) {
			// In rare cases the editor class cannot be
			// instantiated due to classpath/plugin problems.
			// This is so rare that we do not ask the developer
			// to deal with the exception, throwing instead
			// a runtime.
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Set an object with getter and setter methods able to 
	 * hold the values for each item in this editor.
	 * 
	 * The fields as usual must be {@link IFieldWidget}
	 * 
	 * @param ui
	 */
	public void setEditorUI(final Object ui) {
		this.editorUI = ui;
		addValueListeners(editorUI);
	}
	
	/**
	 * Current ui editing.
	 * @return e
	 */
	public Object getEditorUI() {
		return editorUI;
	}
	
	protected void addValueListeners(final Object editorUI) {
		if (beanTemplate==null) throw new RuntimeException("You must set the editing class for with setEditorClass(...) before setting the editorUI object.");
		try {
			BeanUI.addValueListener(beanTemplate, editorUI, new ValueAdapter(getListenerName()) {
				@Override
				public void valueChangePerformed(ValueEvent e) {
					try {
						valueChanged(e);
					} catch (Exception e1) {
						logger.error("Cannot process value changed.", e1);
					}
				}	
			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Override this method to do unique things on value changed.
	 * @throws Exception 
	 */
	protected void valueChanged(ValueEvent e) throws Exception {
		try {
			if (e.getFieldName()!=null)
				BeanUI.uiToBean(editorUI, editorBean, e.getFieldName());
		} catch (NoSuchMethodException ne) {
			// We allow the bean to listen to fields it does not have the value of for now.
			// No action is required.
		}
	}
	
	/**
	 * Note that addValueListener(...) adds listeners to the editing UI
	 * using BeanUI so events will notify if data is changed but do not
	 * encapsulate which item (and do not normally need to).
	 */
	@Override
	public void addValueListener(final ValueListener listener) {
		if (editorUI==null) throw new RuntimeException("You must set the editing UI for each item with setEditorUI(...)");
		if (beanTemplate==null) throw new RuntimeException("You must set the editing class for with setEditorClass(...)");
		try {
			BeanUI.addValueListener(beanTemplate, editorUI, listener);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void removeValueListener(ValueListener l) {
		if (editorUI==null) 
			throw new RuntimeException("You must set the editing UI for each item with setEditorUI(...)");
		if (beanTemplate==null) 
			throw new RuntimeException("You must set the editing class for with setEditorClass(...)");
		try {
			BeanUI.removeValueListener(beanTemplate, editorUI, l);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void dispose() {
		try {
			if (listeners!=null) 
				listeners.clear();
			if (editorUI!=null && beanTemplate!=null) {
				if (editorUI instanceof Widget && ((Widget)editorUI).isDisposed()) 
					return;
				BeanUI.dispose(beanTemplate, editorUI);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			super.dispose();
		}
	}

	@Override
	public Object getValue() {
		return editorBean;
	}
	@Override
	public void setValue(Object value) {
		this.editorBean   = value;
		try {
			off(); // Need to off all beans or get unwanted value changed.
			if (editorUI instanceof Widget && ((Widget)editorUI).isDisposed()) 
				return;
			BeanUI.beanToUI(editorBean, editorUI);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		} finally {
			on();
		}
	}
	
    /**
     * Simply calls setExpressionManager(...) on ay fields this
     * composite has.
     */
	@Override
	public void setExpressionManager(IExpressionManager man) {
		try {
			if (editorUI instanceof Widget && ((Widget)editorUI).isDisposed()) return;
			BeanUI.notify(editorBean, editorUI, new BeanProcessor() {
				@Override
				public void process(Entry<Object, Object> prop, IFieldWidget box) throws Exception {
					if (box instanceof IExpressionWidget) {
						final IExpressionWidget expressionBox = (IExpressionWidget)box;
						if (!expressionBox.isExpressionAllowed()) 
							return;
						final BeanExpressionManager man = new BeanExpressionManager(expressionBox, FieldBeanComposite.this);
						man.setAllowedSymbols(getExpressionFields());
						expressionBox.setExpressionManager(man);
					}
				}
			});
		} catch (Exception e) {
			logger.error("Cannot link expression widgets for "+editorBean, e);
		}
	}
	

	@Override
	public void setExpressionValue(final double value) {
		try {
			if (editorUI instanceof Widget && ((Widget)editorUI).isDisposed()) return;
			BeanUI.notify(editorBean, editorUI, new BeanProcessor() {
				@Override
				public void process(Entry<Object, Object> prop, IFieldWidget box) throws Exception {
					if (box instanceof IExpressionWidget) {
						final IExpressionWidget expressionBox = (IExpressionWidget)box;
						expressionBox.setExpressionValue(value);
					}
				}
			});
		} catch (Exception e) {
			logger.error("Cannot link expression widgets for "+editorBean, e);
		}
	}


	protected List<String> expressionFields;
	/**
	 * Override this method (usually by calling it too) to add values which should be available in expressions.
	 * 
	 * NOTE when overriding that after the first call the expressionFields are cached to avoid too many
	 * interogations of the bean.
	 * 
	 * @return List<String> of possible expression vars.
	 * @throws Exception
	 */
	protected List<String> getExpressionFields() throws Exception {
		if (expressionFields==null)
			expressionFields = BeanUI.getEditingFields(editorBean, editorUI);
        return expressionFields;
	}

	/**
	 * Do not normally need to call this method but can be used to synchronise
	 * editing bean with data without changing UI values. Typically used when 
	 * editing bean goes from null to not null.
	 * @param bean
	 */
	public void setEditingBean(final Object bean) {
		boolean newBean   = editorBean==null;
		this.editorBean   = bean;
		if (newBean) 
			setValue(bean);
	}
	
	@Override
	public boolean isActivated() {
		return active;
	}

	@Override
	public void setActive(boolean active) {
		this.active = active;
		if (activeMode==ACTIVE_MODE.SET_VISIBLE_AND_ACTIVE)
		    setVisible(active);
		else if (activeMode==ACTIVE_MODE.SET_ENABLED_AND_ACTIVE)
			setVisible(active);
	}

	public ACTIVE_MODE getActiveMode() {
		return activeMode;
	}

	public void setActiveMode(ACTIVE_MODE activeMode) {
		this.activeMode = activeMode;
	}

	@Override
	public boolean isOn() {
		return isOn;
	}

	@Override
	public void off() {
		if (editorUI==null)
			throw new RuntimeException("You must set the editing UI with setEditorUI(...)");
		if (beanTemplate==null)
			throw new RuntimeException("You must set the editing class for with setEditorClass(...)");
		isOn = false;
		updateState(isOn);
	}

	@Override
	public void on() {
		if (editorUI==null)
			throw new RuntimeException("You must set the editing UI for each item with setEditorUI(...)");
		if (beanTemplate==null)
			throw new RuntimeException("You must set the editing class for with setEditorClass(...)");
		isOn = true;
		updateState(isOn);
	}

	protected void updateState(boolean isOn) {
		try {
			BeanUI.switchState(beanTemplate, editorUI, isOn);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public boolean isEnabled() {
		return isEnabled;
	}

	public void addBeanSelectionListener(final BeanSelectionListener l) {
		if (listeners==null) 
			listeners = new HashSet<BeanSelectionListener>(7);
		listeners.add(l);
	}
	
	/**
	 * Set enabled state of all contained widgets
	 */
	@Override
	public void setEnabled(final boolean isEnabled) {
		this.isEnabled  = isEnabled;
		try {
			BeanUI.setEnabled(beanTemplate, editorUI, isEnabled);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getListenerName() {
		return listenerName;
	}

	public void setListenerName(String listenerName) {
		this.listenerName = listenerName;
	}

	@Override
	public Object getBean() {
		return editorBean;
	}

	@Override
	public Object getInstance() throws Exception {
		return beanTemplate.getClass().newInstance();
	}

	@Override
	public IFieldWidget getField(String fieldName) throws Exception {
		return BeanUI.getFieldWiget(fieldName, editorUI);
	}

	@Override
	public Object getFieldValue(String fieldName) throws Exception {
		return getField(fieldName).getValue();
	}

	@Override
	public Control getControl() {
		return null;
	}
	
	@Override
	public boolean isExpressionParseRequired(String value) {
		return true;
	}

	@Override
	public boolean isExpressionAllowed() {
		return true;
	}
	
}