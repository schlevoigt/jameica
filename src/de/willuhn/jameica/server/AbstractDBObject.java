/**********************************************************************
 * $Source: /cvsroot/jameica/jameica/src/de/willuhn/jameica/server/Attic/AbstractDBObject.java,v $
 * $Revision: 1.11 $
 * $Date: 2003/11/27 00:22:18 $
 * $Author: willuhn $
 * $Locker:  $
 * $State: Exp $
 *
 * Copyright (c) by willuhn.webdesign
 * All rights reserved
 *
 **********************************************************************/
package de.willuhn.jameica.rmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

import de.willuhn.jameica.Application;
import de.willuhn.jameica.ApplicationException;

/**
 * Basisklasse fuer alle Business-Objekte 
 * @author willuhn
 */
public abstract class AbstractDBObject extends UnicastRemoteObject implements DBObject 
{

  // Die Datenbank-Verbindung
  protected Connection conn;

  // Der Primary-Key des Objektes
  private String id;

  // Haelt die Eigenschaften des Objektes.
  private HashMap properties = new HashMap();
  
  // Haelt die Datentypen der Properties.
  private HashMap types      = new HashMap();

  // definiert, ob das Objekt gerade in einer manuellen Transaktion ist
  private boolean inTransaction = false;

  // ein Cache fuer ForeignObjects
  private HashMap foreignObjectCache = new HashMap();

  /**
   * ct
   * @param conn die JDBC-Connection.
   * @param id ID des zu ladenden Objektes oder null, wenn es neu angelegt werden soll.
   * @throws RemoteException
   */
	public AbstractDBObject(Connection conn, String id) throws RemoteException
	{
		super(); // Konstruktor von UnicastRemoteObject
    Application.getLog().debug("loading new object from database");
		this.conn = conn;
    try {
      this.conn.setAutoCommit(false); // Auto-Commit schalten wir aus weil wir vorsichtig sind ;)
    }
    catch (SQLException e)
    {
      Application.getLog().error("  unable to set autocommit to false.");
      throw new RemoteException("unable to set autocommit to false.",e);
    }
		if (this.conn == null) {
      Application.getLog().error("  connection is null");
      throw new RemoteException("Connection is null");
		}
		init();
		load(id);
	}

	/**
   * Holt sich die Meta-Daten der Tabelle und erzeugt die Properties.
   */
  private void init() throws RemoteException
  {
		String tableName = getTableName();
		ResultSet meta = null;
    Application.getLog().debug("trying to read meta data from table " + tableName);
		try {
			meta = conn.getMetaData().getColumns(null,null,tableName,null);
			String field;
			while (meta.next())
			{
				field = meta.getString(4);
				if (field == null || field.equalsIgnoreCase("id")) // skip empty fields and ID field
					continue;
				properties.put(field,null);
        types.put(field,meta.getString(6));
        // System.out.println("FELD: " + field + ": " + meta.getString(6));
			}
      Application.getLog().debug("  done");
		}
		catch (SQLException e)
		{
			Application.getLog().error("  error while reading meta data");
      e.printStackTrace();
			throw new RemoteException("unable to get metadata from table " + tableName,e);
		}
		finally {
			try {
				meta.close();
			} catch (Exception e) {/*useless*/}
		}
  	
  }

  /**
   * @see de.willuhn.jameica.rmi.DBObject#load(java.lang.String)
   */
  public final void load(String id) throws RemoteException
	{
		this.id = ((id == null || id.equals("")) ? null : id);
		if (this.id == null)
			return; // nothing to load

		String tableName = getTableName();
		Statement stmt = null;
		ResultSet data = null;
    Application.getLog().debug("trying to load object id ["+id+"] from table " + tableName);
		try {
			stmt = conn.createStatement();
			data = stmt.executeQuery("select * from " + tableName + " where id = "+this.id);
			if (!data.next())
				return; // record not found.

			String[] fields = getFields();
			for (int i=0;i<fields.length;++i)
			{
				setField(fields[i],data.getObject(fields[i]));
			}
      Application.getLog().debug("  done");
		}
		catch (SQLException e)
		{
			Application.getLog().error("  error while loading data from table " + tableName);
      e.printStackTrace();
			throw new RemoteException("unable to load data from table " + tableName,e);
		}
		finally {
			try {
				data.close();
				stmt.close();
			} catch (SQLException se) {/*useless*/}
		}
		 
	}
  
  /**
   * @see de.bbvag.dhl.easylog.objects.DBObject#store()
   */
  public final void store() throws RemoteException, ApplicationException
  {
    if (isNewObject())
      insert();
    else 
      update();
    
  }

  /**
   * @see de.willuhn.jameica.rmi.DBObject#clear()
   */
  public final void clear() throws RemoteException
  {
    this.id = null;
    String fields[] = this.getFields();
    for (int i=0;i<fields.length;++i)
    {
      this.setField(fields[i],null);
    }
  }
  
  /**
   * @see de.bbvag.dhl.easylog.objects.DBObject#delete()
   */
  public final void delete() throws RemoteException, ApplicationException
  {
    if (isNewObject())
      return; // no, we delete no new objects ;)

    deleteCheck();

		Statement stmt = null;
    Application.getLog().debug("deleting object id ["+id+"] from table " + getTableName());
    try {
    	stmt = conn.createStatement();
      stmt.execute("delete from " + getTableName() + " where id = '"+id+"'");
      if (!this.inTransaction)
        conn.commit();
      this.id = null;
      Application.getLog().debug("  done");
    }
    catch (SQLException e)
    {
      String msg = "  error while deleting id " + id + " from table " + getTableName();
      Application.getLog().error(msg);
      if (!this.inTransaction) {
        try {
          conn.rollback();
          e.printStackTrace();
          Application.getLog().warn("rollback successful");
        }
        catch (SQLException e2)
        {
          Application.getLog().error("rollback failed");
        }
        throw new RemoteException(msg,e);
      }
    }
    finally {
			try {
				stmt.close();
			} catch (SQLException se) {/*useless*/}
    }
  }

  /**
   * @see de.bbvag.dhl.easylog.objects.DBObject#getID()
   */
  public final String getID() throws RemoteException
  {
    return id;
  }

  /**
   * @see de.willuhn.jameica.rmi.DBObject#getField(java.lang.String)
   */
  public final Object getField(String fieldName) throws RemoteException
  {
    Object o = properties.get(fieldName);
    if (o == null)
      return null;

    // wir checken erstmal, ob es sich um ein Objekt aus einer Fremdtabelle
    // handelt. Wenn das der Fall ist, liefern wir das statt der
    // lokalen ID aus.
    Class foreign = getForeignObject(fieldName);
    if (foreign != null)
    {
      try {
        DBObject cachedObject = (DBObject) foreignObjectCache.get(foreign);
        if (cachedObject != null)
        {
          cachedObject.load(o.toString());
        }
        else {
          cachedObject = Application.getDefaultDatabase().createObject(foreign, o.toString());
          foreignObjectCache.put(foreign,cachedObject);
        }
        return cachedObject;
      }
      catch (Exception e)
      {
        if (Application.DEBUG)
          e.printStackTrace();
        Application.getLog().error("unable to create foreign object for field " + fieldName);
        return o;
      }
    }

    return o;
  }

  /**
   * @see de.willuhn.jameica.rmi.DBObject#getFieldType(java.lang.String)
   */
  public final String getFieldType(String fieldName) throws RemoteException
  {
    try {
      return (String) types.get(fieldName);
    }
    catch (Exception e)
    {
      throw new RemoteException("unable to determine");
    }
  }

  /**
   * Speichert einen neuen Wert in den Properties
   * und liefert den vorherigen zurueck.
   * @param fieldName Name des Feldes.
   * @param value neuer Wert des Feldes. Muss vom Typ String, Date, Timestamp, Double oder Integer sein.
   * @return vorheriger Wert des Feldes.
   */
  protected final Object setField(String fieldName, Object value)
  {
    if (fieldName == null)
      return null;

    return properties.put(fieldName, value);
  }

  /**
   * Liefert ein String-Array mit allen Feldnamen dieses Objektes. 
   * @return
   */
  protected final String[] getFields()
  {
    Set s = properties.keySet();
    return (String[]) s.toArray(new String[s.size()]);
    
  }

	/**
   * Wird bei einem Insert aufgerufen, ermittelt die ID des erzeugten Datensatzes und speichert sie
   * in diesem Objekt.
   * @return
   */
  private void setLastId()
	{
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select max(id) from " + getTableName());
			rs.next();
			this.id = rs.getString(1);
			Application.getLog().debug("  id: " + id);
		}
		catch (Exception e)
		{
			Application.getLog().error("  unable to determine insert id");
		}
	}

  /**
   * Speichert das Objekt als neuen Datensatz in der Datenbank.
   */
  private void insert() throws RemoteException, ApplicationException
  {
    insertCheck();
    id = null;
		PreparedStatement stmt = null;
    try {
      Application.getLog().debug("trying to insert new object into table " + getTableName());
      stmt = getInsertSQL();
      stmt.execute();
      setLastId();
      if (!this.inTransaction)
  			conn.commit();
      Application.getLog().debug("  done");
    }
    catch (SQLException e)
    {
      if (!this.inTransaction) {
        String msg = "error while insert into table " + getTableName();
        Application.getLog().error(msg);
        try {
          conn.rollback();
          e.printStackTrace();
          Application.getLog().warn("rollback successful");
        }
        catch (SQLException e2)
        {
          Application.getLog().error("rollback failed");
        }
        throw new RemoteException(msg,e);
      }
    }
		finally {
			try {
				stmt.close();
			} catch (SQLException se) {/*useless*/}
		}
  }
  
  /**
   * Aktualisiert das Objekt in der Datenbank
   */
  private void update() throws RemoteException, ApplicationException
  {
    updateCheck();
		PreparedStatement stmt = null;
    int affected = 0;
    try {
      Application.getLog().debug("trying to update object id ["+id+"] from table " + getTableName());
			stmt = getUpdateSQL();
      affected = stmt.executeUpdate();
      if (affected != 1)
      {
        // Wenn nicht genau ein Datensatz geaendert wurde, ist was faul.
        throw new SQLException();
      }
      if (!this.inTransaction)
        conn.commit();
      Application.getLog().debug("  done");
    }
    catch (SQLException e)
    {
      if (!this.inTransaction) {
        String msg = "error while update in table " + getTableName();
        Application.getLog().error(msg);
        try {
          conn.rollback();
          e.printStackTrace();
          Application.getLog().warn("rollback successful");
        }
        catch (SQLException e2)
        {
          Application.getLog().error("rollback failed");
        }
        throw new RemoteException(msg,e);
      }
    }
		finally {
			try {
				stmt.close();
			} catch (SQLException se) {/*useless*/}
		}
    
  }

  /**
   * Liefert das automatisch erzeugte SQL-Statement fuer ein Update.
   * Kann bei Bedarf �berschrieben um ein vom dynamisch erzeugten
   * abweichendes Statement f�r die Speicherung zu verwenden.  
   * @return das erzeugte SQL-Statement.
   */
  protected PreparedStatement getUpdateSQL() throws RemoteException
  {
    String sql = "update " + getTableName() + " set ";
    String[] fields = getFields();

    for (int i=0;i<fields.length;++i)
    {
			if (fields[i].equalsIgnoreCase("id"))
				continue; // skip the id field
      sql += fields[i] + "=?,";
    }
    sql = sql.substring(0,sql.length()-1) + " where id="+id+""; // remove last ","
    try {
      PreparedStatement stmt = conn.prepareStatement(sql);
      for (int i=0;i<fields.length;++i)
      {
        String type  = (String) types.get(fields[i]);
        Object value = properties.get(fields[i]);
        setStmtValue(stmt,i,type,value);
      }
      return stmt;
    }
    catch (Exception e)
    {
      throw new RemoteException("unable to prepare update sql statement",e);
    }
  }
  
  /**
   * Liefert das automatisch erzeugte SQL-Statement fuer ein Insert.
   * Kann bei Bedarf �berschrieben um ein vom dynamisch erzeugten
   * abweichendes Statement f�r die Speicherung zu verwenden.  
   * @return das erzeugte SQL-Statement.
   */
  protected PreparedStatement getInsertSQL() throws RemoteException
  {

    String sql = "insert into " + getTableName() + " ";
    String[] fields = getFields();

    String names = "(";
    String values = " values (";

    for (int i=0;i<fields.length;++i)
    {
      if (fields[i] == null || fields[i].equals("")) // die sollte es zwar eigentlich nicht geben, aber sicher ist sicher ;)
        continue; // skip empty fields
      names += fields[i] + ",";
      values += "?,";
    }
    names = names.substring(0,names.length()-1) + ")"; // remove last "," and append ")"
    values = values.substring(0,values.length()-1) + ")"; // remove last "," and append ")"

    try {
      PreparedStatement stmt = conn.prepareStatement(sql + names + values);
      for (int i=0;i<fields.length;++i)
      {
        String type  = (String) types.get(fields[i]);
        Object value = properties.get(fields[i]);
        setStmtValue(stmt,i,type,value);
      }
      return stmt;
    }
    catch (Exception e)
    {
      throw new RemoteException("unable to prepare insert sql statement",e);
    }
  }

  /**
   * Macht sozusagen das Typ-Mapping bei Insert und Update.
   * @param stmt
   * @param index
   * @param type
   * @param value
   */
  private void setStmtValue(PreparedStatement stmt, int index, String type, Object value)
  {
    index++;  // Wer zur Hoelle hat sich ausgedacht, dass Arrays bei Index 0, PreparedStatements aber bei 1 anfangen?? Grr
    try {
      if (type == null || value == null)
        stmt.setNull(index,Types.NULL);

      else if (FIELDTYPE_DATE.equalsIgnoreCase(type))
        stmt.setDate(index,new java.sql.Date(((Date) value).getTime()));

      else if (FIELDTYPE_INT.equalsIgnoreCase(type))
        stmt.setInt(index,((Integer) value).intValue());

      else if (FIELDTYPE_DOUBLE.equalsIgnoreCase(type))
        stmt.setDouble(index,((Double) value).doubleValue());

      else stmt.setString(index,(String) value);
    }
    catch (Exception e)
    {
      try {
        stmt.setString(index,""+value);
      }
      catch (Exception e2) {/* useless */}
    }
  }

  /**
   * Gibt an, ob das Objekt neu ist und somit ein Insert statt einem Update gemacht werden muss.
   * @return
   */
  public final boolean isNewObject() throws  RemoteException
  {
    return id == null;
  }

  /**
   * Liefert den Namen der repraesentierenden SQL-Tabelle.
   * Muss von allen abgeleiteten Klassen implementiert werden.
   * @return Name der repraesentierenden SQL-Tabelle.
   * @throws RemoteException
   */
  protected abstract String getTableName() throws RemoteException;

  /**
   * @see de.willuhn.jameica.rmi.DBObject#getPrimaryField()
   */
  public abstract String getPrimaryField() throws RemoteException;

  /**
   * Diese Methode wird intern vor der Ausfuehrung von delete()
   * aufgerufen. Sie muss �berschrieben werden, damit das Fachobjekt
   * vor dem Durchf�hren der L�schaktion Pr�fungen vornehmen kann.
   * Z.Bsp. ob eventuell noch Abhaengigkeiten existieren und
   * das Objekt daher nicht gel�scht werden kann.
   * @throws ApplicationException wenn das Objekt nicht gel�scht werden darf.
   */
  protected abstract void deleteCheck() throws ApplicationException;

  /**
   * Diese Methode wird intern vor der Ausfuehrung von insert()
   * aufgerufen. Sie muss �berschrieben werden, damit das Fachobjekt
   * vor dem Durchf�hren der Speicherung Pr�fungen vornehmen kann.
   * Z.Bsp. ob alle Pflichtfelder ausgef�llt sind und korrekte Werte enthalten.
   * @throws ApplicationException wenn das Objekt nicht gespeichert werden darf.
   */
  protected abstract void insertCheck() throws ApplicationException;

  /**
   * Diese Methode wird intern vor der Ausfuehrung von update()
   * aufgerufen. Sie muss �berschrieben werden, damit das Fachobjekt
   * vor dem Durchf�hren der Speicherung Pr�fungen vornehmen kann.
   * Z.Bsp. ob alle Pflichtfelder ausgef�llt sind und korrekte Werte enthalten.
   * @throws ApplicationException wenn das Objekt nicht gespeichert werden darf.
   */
  protected abstract void updateCheck() throws ApplicationException;

  /**
   * Prueft, ob das angegebene Feld ein Fremschluessel zu einer
   * anderen Tabelle ist. Wenn das der Fall ist, liefert es die
   * Klasse, die die Fremd-Tabelle abbildet. Andernfalls null.
   * @param field
   * @return
   * @throws RemoteException
   */
  protected abstract Class getForeignObject(String field) throws RemoteException;
  
  /**
   * @see de.bbvag.dhl.easylog.objects.DBObject#transactionBegin()
   */
  public final void transactionBegin() throws RemoteException
  {
    if (this.inTransaction)
      return;

    this.inTransaction = true;
    Application.getLog().debug("starting new transaction");
  }

  /**
   * @see de.bbvag.dhl.easylog.objects.DBObject#transactionRollback()
   */
  public final void transactionRollback() throws RemoteException
  {
    if (!this.inTransaction)
      return;

    try {
      conn.rollback();
      this.inTransaction = false;
      Application.getLog().debug("connection rolled back");
    }
    catch (SQLException e)
    {
      e.printStackTrace();
      Application.getLog().error("rollback failed");
      throw new RemoteException("rollback failed",e);
    }

  }  

  /**
   * @see de.bbvag.dhl.easylog.objects.DBObject#transactionCommit()
   */
  public final void transactionCommit() throws RemoteException
  {
    if (!this.inTransaction)
      return;

    Application.getLog().debug("committing open transaction");
    try {
      conn.commit();
      this.inTransaction = false;
    }
    catch (SQLException se)
    {
      se.printStackTrace();
      Application.getLog().error("commit failed");
      try {
        conn.rollback();
        this.inTransaction = false;
        Application.getLog().warn("rollback successful");
      }
      catch (SQLException se2)
      {
        Application.getLog().error("rollback failed");
      }
      throw new RemoteException("commit failed",se);
    }

  }
  
  /**
   * @see de.willuhn.jameica.rmi.DBObject#getList()
   */
  public final DBIterator getList() throws RemoteException
  {
    return Application.getDefaultDatabase().createList(this.getClass());
  }

}

/*********************************************************************
 * $Log: AbstractDBObject.java,v $
 * Revision 1.11  2003/11/27 00:22:18  willuhn
 * @B paar Bugfixes aus Kombination RMI + Reflection
 * @N insertCheck(), deleteCheck(), updateCheck()
 * @R AbstractDBObject#toString() da in RemoteObject ueberschrieben (RMI-Konflikt)
 *
 * Revision 1.10  2003/11/24 23:01:58  willuhn
 * @N added settings
 *
 * Revision 1.9  2003/11/24 17:27:50  willuhn
 * @N Context menu in table
 *
 * Revision 1.8  2003/11/24 16:25:53  willuhn
 * @N AbstractDBObject is now able to resolve foreign keys
 *
 * Revision 1.7  2003/11/24 14:21:53  willuhn
 * *** empty log message ***
 *
 * Revision 1.6  2003/11/22 20:43:05  willuhn
 * *** empty log message ***
 *
 * Revision 1.5  2003/11/21 02:10:21  willuhn
 * @N prepared Statements in AbstractDBObject
 * @N a lot of new SWT parts
 *
 * Revision 1.4  2003/11/20 03:48:42  willuhn
 * @N first dialogues
 *
 * Revision 1.3  2003/11/13 00:37:36  willuhn
 * *** empty log message ***
 *
 * Revision 1.2  2003/11/12 00:58:54  willuhn
 * *** empty log message ***
 *
 * Revision 1.1  2003/11/05 22:46:19  willuhn
 * *** empty log message ***
 *
 * Revision 1.16  2003/10/29 20:56:49  willuhn
 * @N added transactionRollback
 *
 * Revision 1.15  2003/10/29 15:18:15  willuhn
 * @N added transactionBegin() and transactionCommit()
 *
 * Revision 1.14  2003/10/29 11:33:22  andre
 * @N isNewObject
 *
 * Revision 1.13  2003/10/28 19:43:41  willuhn
 * @N Sendungserfassung seems to be complete
 *
 * Revision 1.12  2003/10/28 12:36:57  willuhn
 * *** empty log message ***
 *
 * Revision 1.11  2003/10/27 23:42:31  willuhn
 * *** empty log message ***
 *
 * Revision 1.10  2003/10/27 23:36:39  willuhn
 * @N debug messages
 *
 * Revision 1.9  2003/10/27 23:16:23  willuhn
 * @C AbstractDBObject.getField returns null when field does not exist
 *
 * Revision 1.8  2003/10/27 22:50:19  willuhn
 * @N Uebergabeparameter an Views
 *
 * Revision 1.7  2003/10/27 19:03:28  willuhn
 * @R removed unused statement
 *
 * Revision 1.6  2003/10/27 17:35:37  willuhn
 * @B typo
 *
 * Revision 1.5  2003/10/27 16:31:53  willuhn
 * @N Preise werden jetzt aus der Datenbank gelesen
 *
 * Revision 1.4  2003/10/27 11:49:12  willuhn
 * @N added DBIterator
 *
 * Revision 1.3  2003/10/26 19:22:10  willuhn
 * @B fixes in object mapper
 *
 * Revision 1.2  2003/10/26 17:46:30  willuhn
 * @N DBObject
 *
 * Revision 1.1  2003/10/25 19:49:47  willuhn
 * *** empty log message ***
 *
 **********************************************************************/