package org;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringBufferInputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import com.csvreader.CsvWriter;
import com.csvreader.CsvReader;
import sun.misc.BASE64Decoder;


public class IssueTranslator {

  public static final String PROJECT_KEY = "DS";

  Map<String,String> userMap;
  Set<String> users = new TreeSet<String>();
  int attachmentTotal = 0;
  int attachmentWritten = 0;

  public static void main(String[] args) {

    if (args.length < 5) {
      System.out.println("Usage: java IssueTranslator issues.xml usermapping.csv issues.jelly issues.csv attachmentDir");
    }
    IssueTranslator translator = new IssueTranslator();
    translator.translate(args[0], args[1], args[2], args[3], args[4]);

  }

  public static String toCSV(List<?> list)
  {
    StringBuilder builder = new StringBuilder();
    if (list.size() > 0)
    {
      builder.append(list.get(0));
      for (int i = 1; i < list.size(); i++)
      {
        builder.append(',');
        builder.append(list.get(i));
      }
    }
    return builder.toString();
  }

  public void translate(String fileIn, String userCSV, String fileOut, String csvFileOut, String attachmentPath) {

    SAXBuilder builder = new SAXBuilder();

    try {
      userMap = this.readUserMapping(userCSV);

      File attachmentDir = new File(attachmentPath);
      attachmentDir.mkdir();

      List<Issue> issueTrackerIssues = new LinkedList<Issue>();

      Document doc = builder.build(fileIn);
      Element root = doc.getRootElement();
      List<Element> issues = root.getChildren("issue");
      for (Element e : issues) {
        List<Blocks> blocks = new LinkedList<Blocks>();
        List<Duplicate> dups = new LinkedList<Duplicate>();
        List<LongDesc> longDescs = new LinkedList<LongDesc>();
        List<Activity> activities = new LinkedList<Activity>();
        List<Attachment> attachments = new LinkedList<Attachment>();

        List<Element> blocksKids = e.getChildren("blocks");
        for (Element kid : blocksKids) {
          String who = registerUser(kid.getChildText("who"));
          Blocks block = new Blocks(kid.getChildText("issue_id"),
                                    who,
                                    kid.getChildText("when"));
          blocks.add(block);
        }

        List<Element> dupsKids = e.getChildren("is_duplicate");
        for (Element kid : dupsKids) {
          String who = registerUser(kid.getChildText("who"));
          Duplicate dup = new Duplicate(kid.getChildText("issue_id"),
                                        who,
                                        kid.getChildText("when"));
          dups.add(dup);
        }

        List<Element> long_descKids = e.getChildren("long_desc");
        for (Element kid : long_descKids) {
          String who = registerUser(kid.getChildText("who"));
          LongDesc longDesc = new LongDesc(who,
                                           kid.getChildText("issue_when"),
                                           kid.getChildText("thetext"));
          longDescs.add(longDesc);
        }

        List<Element> activityKids = e.getChildren("activity");
        for (Element kid : activityKids) {
          activities.add(new Activity(
            kid.getChildText("user"), kid.getChildText("when"),
            kid.getChildText("field_name"), kid.getChildText("field_desc"),
            kid.getChildText("oldvalue"), kid.getChildText("newvalue")
          ));
        }

        String issue_id = e.getChildText("issue_id");

        List<Element> attachmentKids = e.getChildren("attachment");
        for (Element kid : attachmentKids)
        {
          attachmentTotal++;
          String data = kid.getChildText("data");
          if (data != null && data.length() > 0)
          {
            String issueDir = attachmentDir.getAbsolutePath() + File.separator +
                 PROJECT_KEY + "-" + issue_id;
            Attachment attachment =
                 new Attachment(issueDir,
                                kid.getChildText("mimetype"),
                                kid.getChildText("date"),
                                kid.getChildText("desc"),
                                kid.getChildText("filename"),
                                kid.getChildText("username"),
                                kid.getChildText("url"));
            if (attachment.writeFile(data))
            {
              attachmentWritten++;
            }
            attachments.add(attachment);
          }
        }

        List<Element> ccKids = e.getChildren("cc");

        List<String> cc = new ArrayList<String>(ccKids.size());
        for (Element elem : ccKids)
        {
          cc.add(elem.getText());
        }

        String keywords = e.getChildText("keywords");

        ArrayList<String> docAssessment = new ArrayList<String>();
        ArrayList<String> docStatus = new ArrayList<String>();
        ArrayList<String> otherKeywords = new ArrayList<String>();
        ArrayList<String> qaFlags = new ArrayList<String>();
        ArrayList<String> qaTestCategories = new ArrayList<String>();
        ArrayList<String> qaEval = new ArrayList<String>();
        ArrayList<String> qaStatus = new ArrayList<String>();

        if (keywords != null)
        {
          String[] words = keywords.split(",");
          for (String word : words)
          {
            String keyword = word.trim();
            if (keyword.equals("doc_not_impacted"))
            {
              docAssessment.add(keyword);
            }
            else if (keyword.equals("doc_needed"))
            {
              docAssessment.add(keyword);
              docStatus.add("New");
            }
            else if (keyword.equals("doc_complete"))
            {
              docAssessment.add("doc_needed");
              docStatus.add("Complete");
            }
            else if (keyword.equals("qa_seen"))
            {
              qaEval.add("seen");
            }
            else if (keyword.equals("qa_internal_feature"))
            {
              qaFlags.add("internal_feature");
            }
            else if (keyword.equals("qa_api_test"))
            {
              qaFlags.add("internal_api");
            }
            else if (keyword.equals("qa_no_cli"))
            {
              qaFlags.add("no_cli");
            }
            else if (keyword.equals("qa_no_spec"))
            {
              qaFlags.add("no_spec");
            }
            else if (keyword.equals("qa_no_doc"))
            {
              qaFlags.add("no_doc");
            }
            else if (keyword.equals("qa_unit_test"))
            {
              qaTestCategories.add("unit");
            }
            else if (keyword.equals("qa_functional_test"))
            {
              qaTestCategories.add("functional");
            }
            else if (keyword.equals("qa_system_test"))
            {
              qaTestCategories.add("system");
            }
            else if (keyword.equals("qa_interop_test"))
            {
              qaTestCategories.add("interop");
            }
            else if (keyword.equals("qa_perf_test"))
            {
              qaTestCategories.add("perf");
            }
            else if (keyword.equals("qa_integration_test"))
            {
              qaTestCategories.add("integration");
            }
            else if (keyword.equals("qa_manual_test"))
            {
              qaTestCategories.add("manual");
            }
            else if (keyword.equals("qa_wait_input"))
            {
              qaStatus.add("wait_input");
            }
            else if (keyword.equals("qa_blocked"))
            {
              qaStatus.add("blocked");
            }
            else if (keyword.equals("qa_will_not_test"))
            {
              qaStatus.add("will_not_test");
            }
            else
            {
              otherKeywords.add(keyword);
            }
          }
        }

        if (docAssessment.size() > 1)
        {
          System.out.println("WARNING: Issue " + issue_id +
               " has multiple values of Doc Assessment: " + docAssessment);
        }
        else if (docAssessment.size() == 0)
        {
          docAssessment.add("doc_not_assessed");
        }

        if (docStatus.size() > 1)
        {
          System.out.println("WARNING: Issue " + issue_id +
               " has multiple values of Doc Status: " + docStatus);
        }
        else if (docAssessment.size() == 0)
        {
          docAssessment.add("N/A");
        }

        if (qaStatus.size() > 1)
        {
          System.out.println("WARNING: Issue " + issue_id +
               " has multiple values of QA Status: " + qaStatus);
        }
        else if (qaStatus.size() == 0)
        {
          qaStatus.add("not_started");
        }

        if (qaEval.size() > 1)
        {
          System.out.println("WARNING: Issue " + issue_id +
               " has multiple values of QA Evaluation: " + qaEval);
        }
        else if (qaEval.size() == 0)
        {
          qaEval.add("unseen");
        }

        // Convert delta_ts date format.
        SimpleDateFormat f1 = new SimpleDateFormat("yyyyMMddhhmmss");
        SimpleDateFormat f2 = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String changed = e.getChildText("delta_ts");
        changed = f2.format(f1.parse(changed));

        String assignedTo = registerUser(e.getChildText("assigned_to"));
        String reporter = registerUser(e.getChildText("reporter"));
        String qaContact = registerUser(e.getChildText("qa_contact"));

        Issue issue = new Issue(
          issue_id,
          e.getChildText("issue_status"),
          e.getChildText("priority"),
          e.getChildText("resolution"),
          e.getChildText("component"),
          e.getChildText("version"),
          e.getChildText("rep_platform"),
          assignedTo,
          changed,
          e.getChildText("subcomponent"),
          reporter,
          e.getChildText("target_milestone"),
          e.getChildText("issue_type"),
          e.getChildText("creation_ts"),
          qaContact,
          e.getChildText("status_whiteboard"),
          e.getChildText("issue_file_loc"),
          e.getChildText("votes"),
          e.getChildText("op_sys"),
          e.getChildText("short_desc"),
          toCSV(otherKeywords), toCSV(cc), toCSV(docAssessment),
          toCSV(docStatus),
          toCSV(qaEval), toCSV(qaFlags), toCSV(qaTestCategories),
          toCSV(qaStatus),
          activities, longDescs, blocks,
          dups, attachments);
        issueTrackerIssues.add(issue);
      }


      Namespace jiraNS = Namespace.getNamespace("jira", "jelly:com.atlassian.jira.jelly.enterprise.JiraTagLib");
      root = new Element("JiraJelly");
      root.addNamespaceDeclaration(jiraNS);
      Document docOut = new Document(root);

      CsvWriter csv = new CsvWriter(csvFileOut);

      LongDesc headerDesc = new LongDesc("Who", "When", "Description");
      ArrayList<LongDesc> headerDescs = new ArrayList<LongDesc>();
      headerDescs.add(headerDesc);
      Issue header = new Issue(
           "Issue",
           "State",
           "Priority",
           "Resolution",
           "Component",
           "Version",
           "Platform",
           "Owner",
           "Changed",
           "Subcomponent",
           "Reporter",
           "Target Milestone",
           "Type",
           "Opened",
           "QA Contact",
           "Status Whiteboard",
           "URL",
           "Votes",
           "OS",
           "Summary",
           "Keywords",
           "CC",
           "Doc Assessment",
           "Doc Status",
           "QA Evaluation",
           "QA Flags",
           "QA Tests",
           "QA Status",
           null, headerDescs, null, null, null);
      header.writeCsvRecord(csv);

//      for (Issue issue : issueTrackerIssues) {
//        if (issue.cc != null && issue.cc.indexOf(',') != -1)
//        {
//          // JIRA doesn't import multiple users from CSV into a custom
//          // Multi User Picker field.
//          String users = issue.cc.substring(issue.cc.indexOf(',')+1);
//          String text = "CC " + users + " on issue " + issue.id;
//          Comment xmlComment = new Comment(text);
//          root.addContent(xmlComment);
//        }
//      }

      for (Issue issue : issueTrackerIssues) {

        issue.writeCsvRecord(csv);

        String issueKey = PROJECT_KEY + "-" + issue.id;

        for (int i = 1; i < issue.longDescriptions.size(); i++)
        {
          LongDesc desc = issue.longDescriptions.get(i);
          Element addComment = new Element("AddComment", jiraNS);
          addComment.setAttribute("issue-key", issueKey);
          addComment.setAttribute("commenter", desc.who);
          addComment.setAttribute("created", desc.issueWhen);
          addComment.setAttribute("updated", desc.issueWhen);
          addComment.setAttribute("editedBy", desc.who);
          addComment.setAttribute("comment", desc.thetext);
          root.addContent(addComment);
        }

        for (Blocks block : issue.blocks)
        {
          Element addBlock = new Element("LinkIssue", jiraNS);
          addBlock.setAttribute("key", issueKey);
          addBlock.setAttribute("linkKey", PROJECT_KEY + "-" + block.issueId);
          addBlock.setAttribute("linkDesc", "blocks");
          root.addContent(addBlock);
        }

        for (Duplicate duplicate : issue.duplicates)
        {
          Element addDuplicate = new Element("LinkIssue", jiraNS);
          addDuplicate.setAttribute("key", issueKey);
          addDuplicate.setAttribute("linkKey", PROJECT_KEY + "-" + duplicate.issueId);
          addDuplicate.setAttribute("linkDesc", "duplicates");
          root.addContent(addDuplicate);
        }

        for (Attachment attachment : issue.attachments)
        {
          String filepath = attachment.pathname + File.separator + attachment.filename;
          Element addAttachment = new Element("AttachFile", jiraNS);
          addAttachment.setAttribute("key", issueKey);
          addAttachment.setAttribute("filepath", filepath);
          addAttachment.setAttribute("option", "add");
          root.addContent(addAttachment);
        }
      }

      csv.close();

      Format format = Format.getPrettyFormat();
      format.setLineSeparator(System.getProperty("line.separator"));
      XMLOutputter out = new XMLOutputter(format);
      FileWriter writer = new FileWriter(fileOut);

      out.output(docOut, writer);

      System.out.println("Parsed and wrote " + issueTrackerIssues.size() + " issues\n");
      System.out.println("Found " + users.size() + " issue tracker users: " + users);
      System.out.println("Wrote " + attachmentWritten + " attachments");

    } catch (JDOMException e) {
      System.out.println(e.getMessage());
      e.printStackTrace();
    }
    catch (IOException e) {
      System.out.println(e.getMessage());
      e.printStackTrace();
    }
    catch (ParseException e)
    {
      System.out.println(e.getMessage());
      e.printStackTrace();
    }
  }

  public Map<String,String> readUserMapping(String userCSV) throws IOException
  {
    HashMap<String,String> userMap = new HashMap<String, String>(100);
    CsvReader csv = new CsvReader(userCSV);
    csv.readHeaders();
    while (csv.readRecord())
    {
      String login = csv.get("login");
      String wikiLogin = csv.get("wikilogin");
      if (wikiLogin == null || wikiLogin.length() == 0)
      {
        wikiLogin = login;
      }
      userMap.put(login,wikiLogin);
    }
    csv.close();
    return userMap;
  }

  public String registerUser(String user)
  {
    if (user != null)
    {
      // Skip email addresses.
      if (user.indexOf('@') == -1)
      {
        String trimmed = user.trim();
        if (trimmed.length() > 0)
        {
          String mappedUser = userMap.get(trimmed);
          users.add(trimmed);
          return mappedUser;
        }
      }
    }
    return "";
  }

  class Issue {
    public String id;
    public String status;
    public String priority;
    public String resolution;
    public String component;
    public String version;
    public String repPlatform;
    public String assignedTo;
    public String deltaTs;
    public String subcomponent;
    public String reporter;
    public String targetMilestone;
    public String issueType;
    public String creationTs;
    public String qaContact;
    public String statusWhiteboard;
    public String url;
    public String votes;
    public String opSys;
    public String shortDesc;
    public String keywords;
    public String cc;
    public String docAssessment;
    public String docStatus;
    public String qaEval;
    public String qaFlags;
    public String qaTestCategories;
    public String qaStatus;
    public List<Activity> activities;
    public List<LongDesc> longDescriptions;
    public List<Blocks> blocks;
    public List<Duplicate> duplicates;
    public List<Attachment> attachments;

    public Issue(String id, String status, String priority, String resolution, String component,
                 String version, String repPlatform, String assignedTo, String deltaTs,
                 String subcomponent, String reporter, String targetMilestone, String issueType,
                 String creationTs, String qaContact, String statusWhiteboard, String issueFileLoc,
                 String votes, String opSys, String shortDesc, String keywords, String cc, String docAssessment,
                 String docStatus, String qaEval, String qaFlags, String qaTestCategories, String qaStatus,
                 List<Activity> activities, List<LongDesc> longdescriptions, List<Blocks> blocks,
                 List<Duplicate> duplicates, List<Attachment> attachments) {
      this.id = id;
      this.status = status;
      this.priority = priority;
      this.resolution = resolution;
      this.component = component;
      this.version = version;
      this.repPlatform = repPlatform;
      this.assignedTo = assignedTo;
      this.deltaTs = deltaTs;
      this.subcomponent = subcomponent;
      this.reporter = reporter;
      this.targetMilestone = targetMilestone;
      this.issueType = issueType;
      this.creationTs = creationTs;
      if (qaContact.indexOf('@') != -1)
      {
        this.qaContact = "";
      }
      else
      {
        this.qaContact = qaContact;
      }
      this.statusWhiteboard = statusWhiteboard;
      this.url = issueFileLoc;
      this.votes = votes;
      this.opSys = opSys;
      this.shortDesc = shortDesc;
      this.keywords = keywords;
      this.cc = cc;
      this.docAssessment = docAssessment;
      this.docStatus = docStatus;
      this.qaEval = qaEval;
      this.qaFlags = qaFlags;
      this.qaTestCategories = qaTestCategories;
      this.qaStatus = qaStatus;
      if (activities == null)
      {
        this.activities = new ArrayList<Activity>();
      }
      else
      {
        this.activities = activities;
      }
      this.activities = activities;
      if (longdescriptions == null)
      {
        this.longDescriptions = new ArrayList<LongDesc>();
      }
      else
      {
        this.longDescriptions = longdescriptions;
      }
      if (blocks == null)
      {
        this.blocks = new ArrayList<Blocks>();
      }
      else
      {
        this.blocks = blocks;
      }
      if (duplicates == null)
      {
        this.duplicates = new ArrayList<Duplicate>();
      }
      else
      {
        this.duplicates = duplicates;
      }
      if (attachments == null)
      {
        this.attachments = new ArrayList<Attachment>();
      }
      else
      {
        this.attachments = attachments;
      }
    }

    public void writeCsvRecord(CsvWriter csv)
         throws IOException
    {
      csv.write(id);
      csv.write(creationTs);
      csv.write(deltaTs);
      csv.write(issueType);
      csv.write(priority);
      csv.write(repPlatform);
      csv.write(assignedTo);
      csv.write(reporter);
      csv.write(status);
      csv.write(resolution);
      csv.write(subcomponent);
      csv.write(version);
      csv.write(opSys);
      csv.write(votes);
      csv.write(targetMilestone);
      csv.write(qaContact);
      csv.write(statusWhiteboard);
      csv.write(keywords);
      csv.write(shortDesc);
//      if (cc != null && cc.indexOf(',') != -1)
//      {
//        // JIRA doesn't import multiple users from CSV into a custom
//        // Multi User Picker field.
//        csv.write(cc.substring(0, cc.indexOf(',')));
//      }
//      else
//      {
//        csv.write(cc);
//      }
      csv.write(cc);
      csv.write(component);
      csv.write(url);
      csv.write(docAssessment);
      csv.write(docStatus);
      csv.write(qaEval);
      csv.write(qaFlags);
      csv.write(qaTestCategories);
      csv.write(qaStatus);

      // The first comment is the bug description.
      if (longDescriptions.size() == 0)
      {
        csv.write(shortDesc);
      }
      else
      {
        csv.write(longDescriptions.get(0).thetext);
      }

      csv.endRecord();
    }
  }
}

class Activity {
  public String user;
  public String when;
  public String fieldName;
  public String fieldDesc;
  public String oldValue;
  public String newValue;

  public Activity(String user, String when, String fieldName, String fieldDesc, String oldValue, String newValue) {
    this.user = user;
    this.when = when;
    this.fieldName = fieldName;
    this.fieldDesc = fieldDesc;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }
}

class LongDesc {
  public String who;
  public String issueWhen;
  public String thetext;

  public LongDesc(String who, String issueWhen, String thetext) {
    this.who = who;
    this.issueWhen = issueWhen;
    this.thetext = thetext;
  }
}


class Blocks {
  public String issueId;
  public String who;
  public String when;

  Blocks(String issueId, String who, String when) {
    this.issueId = issueId;
    this.who = who;
    this.when = when;
  }
}

class Duplicate {
  public String issueId;
  public String who;
  public String when;

  Duplicate(String issueId, String who, String when) {
    this.issueId = issueId;
    this.who = who;
    this.when = when;
  }
}

class Attachment
{
  String pathname; // The local directory.
  String mimetype;
  String date;
  String desc;
  String filename;
  String username;
  String url;

  Attachment(String pathname,
             String mimetype,
             String date,
             String desc,
             String filename,
             String username,
             String url)
  {
    this.pathname = pathname;
    this.mimetype = mimetype;
    this.date = date;
    this.desc = desc;
    this.filename = filename;
    this.username = username;
    this.url = url;
  }

  public boolean writeFile(String data) throws IOException
  {
    File dir = new File(pathname);
    dir.mkdirs();

    File file = new File(dir, filename);
    StringBufferInputStream inputStream = new StringBufferInputStream(data);
    try
    {
      FileOutputStream outputStream = new FileOutputStream(file);

      try
      {
        BASE64Decoder decoder = new BASE64Decoder();
        decoder.decodeBuffer(inputStream, outputStream);
      }
      finally
      {
        outputStream.close();
      }
    }
    finally
    {
      inputStream.close();
    }
    return true;
  }
}

