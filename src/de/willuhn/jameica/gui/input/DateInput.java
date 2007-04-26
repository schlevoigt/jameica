/**********************************************************************
 * $Source: /cvsroot/jameica/jameica/src/de/willuhn/jameica/gui/input/DateInput.java,v $
 * $Revision: 1.5 $
 * $Date: 2007/04/26 11:19:48 $
 * $Author: willuhn $
 * $Locker:  $
 * $State: Exp $
 *
 * Copyright (c) by willuhn.webdesign
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.gui.input;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import de.willuhn.jameica.gui.dialogs.CalendarDialog;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

/**
 * Fix und fertig konfiguriertes Eingabe-Feld fuer die Datumseingabe.
 * Das ist ein Meta-Input-Feld, welches sich unter der Haube aus einem
 * DialogInput und einem CalendarDialog zusammensetzt.
 */
public class DateInput implements Input
{
  private final static Object PLACEHOLDER = new Object();
  
  private DateFormat format = SimpleDateFormat.getDateInstance(DateFormat.DEFAULT,Application.getConfig().getLocale());
  
  private DialogInput input     = null;
  private CalendarDialog dialog = null;
  private String name           = null;
  
  private Object oldValue = PLACEHOLDER;
  
  private boolean mandatory = false;
  
  /**
   * Konstruktor ohne Datumsangabe aber mit Default-Format.
   */
  public DateInput()
  {
    this(null);
  }
  
  /**
   * Konstruktor mit uebergebenem Datum und Default-Format.
   * @param date das Datum.
   */
  public DateInput(Date date)
  {
    this(date,null);
  }
  
  /**
   * Konstruktor mit uebergebenem Datum und Format.
   * @param date das Datum.
   * @param format das Format.
   */
  public DateInput(Date date, DateFormat format)
  {
    if (format != null)
      this.format = format;
    
    this.dialog = new CalendarDialog(CalendarDialog.POSITION_MOUSE);
    
    if (date != null)
      this.dialog.setDate(date);
    
    this.dialog.addCloseListener(new Listener() {
    
      public void handleEvent(Event event)
      {
        if (event == null || event.data == null)
          return;
        input.setText(DateInput.this.format.format((Date) event.data));
      }
    });
    
    this.dialog.setTitle(Application.getI18n().tr("Datum"));
    this.dialog.setText(Application.getI18n().tr("Bitte w�hlen Sie das Datum aus"));
    this.input = new DialogInput(date == null ? null : this.format.format(date),this.dialog);
  }

  /**
   * Deaktiviert das komplette Control (Button und Text).
   * @see de.willuhn.jameica.gui.input.Input#disable()
   */
  public void disable()
  {
    this.input.disable();
  }

  /**
   * Aktiviert das komplette Control (Button und Text).
   * @see de.willuhn.jameica.gui.input.Input#enable()
   */
  public void enable()
  {
    this.input.enable();
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#focus()
   */
  public void focus()
  {
    this.input.focus();
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#getControl()
   */
  public Control getControl()
  {
    return this.input.getControl();
  }

  /**
   * Liefert ein Objekt vom Typ <code>java.util.Date</code> oder null,
   * wenn das Datum nicht geparst werden konnte.
   * @see de.willuhn.jameica.gui.input.Input#getValue()
   */
  public Object getValue()
  {
    // Wir liefern grundsaetzlich den Text aus dem Eingabe-Feld,
    // damit der User das Datum auch manuell eingeben kann.
    String text = this.input.getText();
    if (text == null || text.length() == 0)
      return null;
    try
    {
      Date d = this.format.parse(text);
      
      // Bei der Gelegenheit schreiben wir auch gleich nochmal
      // das Datum schoen formatiert rein
      this.input.setText(this.format.format(d));
      return d;
    }
    catch (ParseException e)
    {
      Logger.error("unable to parse date " + text,e);
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(Application.getI18n().tr("Ung�ltiges Datum: {0}",text),StatusBarMessage.TYPE_ERROR));
      return null;
    }
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#isEnabled()
   */
  public boolean isEnabled()
  {
    return this.input.isEnabled();
  }

  /**
   * Aktiviert oder deaktiviert das komplette Control (Button und Text).
   * @see de.willuhn.jameica.gui.input.Input#setEnabled(boolean)
   */
  public void setEnabled(boolean enabled)
  {
    this.input.setEnabled(enabled);
  }

  /**
   * Aktiviert nur den Text.
   */
  public final void enableClientControl()
  {
    this.input.enableClientControl();
  }
  
  /**
   * Deaktiviert nur den Text.
   */
  public final void disableClientControl()
  {
    this.input.disableClientControl();
  }
  
  /**
   * @see de.willuhn.jameica.gui.input.Input#setValue(java.lang.Object)
   */
  public void setValue(Object value)
  {
    // Wenn nichts uebergeben wird, machen wir nur die Werte leer.
    if (value == null)
    {
      this.input.setValue(null);
      this.input.setText("");
      return;
    }
    
    // Wenn ein String uebergeben wurde, schreiben wir den rein
    if (value instanceof String)
    {
      this.input.setText((String)value);
      
      // Testen aber trotzdem noch, ob das Datum das korrekte Format hat
      try
      {
        this.format.parse((String)value);
      }
      catch (Exception e)
      {
        Logger.error("invalid date " + value,e);
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(Application.getI18n().tr("Ung�ltiges Datum: {0}",value.toString()),StatusBarMessage.TYPE_ERROR));
      }
      return;
    }
    
    // Wenn es ein Date ist, koennen wir es getrost uebernehmen
    if (value instanceof Date)
    {
      this.input.setText(this.format.format((Date)value));
    }
  }
  
  /**
   * Legt den anzuzeigenden Text auf dem Kalender-Dialog fest.
   * @param text Text auf dem Kalender-Dialog.
   */
  public void setText(String text)
  {
    this.dialog.setText(text);
  }
  
  /**
   * Legt den auf dem Kalender-Dialog anzuzeigenden Titel fest.
   * @param title der auf dem Dialog anzuzeigende Titel.
   */
  public void setTitle(String title)
  {
    this.dialog.setTitle(title);
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#addListener(org.eclipse.swt.widgets.Listener)
   */
  public final void addListener(Listener l)
  {
    this.input.addListener(l);
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#paint(org.eclipse.swt.widgets.Composite)
   */
  public final void paint(Composite parent)
  {
    this.input.setMandatory(this.isMandatory());
    this.input.paint(parent);
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#paint(org.eclipse.swt.widgets.Composite, int)
   */
  public final void paint(Composite parent, int width)
  {
    this.input.setMandatory(this.isMandatory());
    this.input.paint(parent,width);
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#setComment(java.lang.String)
   */
  public void setComment(String comment)
  {
    this.input.setComment(comment);
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#isMandatory()
   */
  public boolean isMandatory()
  {
    return this.mandatory;
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#setMandatory(boolean)
   */
  public void setMandatory(boolean mandatory)
  {
    this.mandatory = mandatory;
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#getName()
   */
  public String getName()
  {
    return this.name;
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#setName(java.lang.String)
   */
  public void setName(String name)
  {
    this.name = name;
  }

  /**
   * @see de.willuhn.jameica.gui.input.Input#hasChanged()
   */
  public boolean hasChanged()
  {
    Object newValue = this.input.getText();

    try
    {
      // Wir wurden noch nie aufgerufen
      if (oldValue == PLACEHOLDER || oldValue == newValue)
        return false;

      return newValue == null || !newValue.equals(oldValue);
    }
    finally
    {
      oldValue = newValue;
    }
    
  }
}


/*********************************************************************
 * $Log: DateInput.java,v $
 * Revision 1.5  2007/04/26 11:19:48  willuhn
 * @N Generische Funktion "hasChanged()" zum Pruefen auf Aenderungen in Eingabe-Feldern
 *
 * Revision 1.4  2007/03/19 12:30:06  willuhn
 * @N Input can now have it's own label
 *
 * Revision 1.3  2006/12/28 15:35:52  willuhn
 * @N Farbige Pflichtfelder
 *
 * Revision 1.2  2006/10/10 22:30:07  willuhn
 * @C besseres Text-handling in DateInput
 *
 * Revision 1.1  2006/09/10 12:12:26  willuhn
 * @N Neues kombiniertes Eingabefeld "DateInput"
 *
 **********************************************************************/