package com.bodastage.boda_ericssoncnaiparser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import javax.xml.stream.XMLStreamException;

/**
 * Ericsson CNAI dump parser.
 *
 * @author Bodastage<info@bodastage.com>
 * @version 1.0.0
 * @see http://github.com/bodastage/boda-ericssoncnaiparser
 */
public class BodaCNAIParser 
{
    
    /**
     * The CNAI dump file being parsed.
     * 
     * @since 1.0.0
     */
    static String filename;
    
    /**
     * The capabilities value in the CNAI dump file.
     * 
     * @since 1.0.0
     */
    static String capabilities;
    
    /**
     * The time and date the file was generated.
     * 
     * @since 1.0.0
     */
    static String creationDateTime;
    
    /**
     * The subnetwork value.
     * 
     * @since 1.0.0
     */
    static String subnetwork;
    
    /**
     * The entity identifier.
     * 
     * @since 1.0.1
     */
    static String set;

    /**
     * The previous set value
     * 
     * @since 1.0.1
     */
    static String prevSet;
    
    /**
     * The domain value.
     * 
     */
    static String domain;
    
    /**
     * A list of all the parameters and their value collected for each network 
     * entity under a domain.
     * 
     * @since 1.0.0
     */
    static Map<String, String> domainParameterList = new LinkedHashMap<String, String>();
    
    /**
     * Parser start time. 
     * 
     * @since 1.0.0
     */
    final static long startTime = System.currentTimeMillis();
            
    /**
     * Output directory.
     *
     * @since 1.0.0
     */
    static String outputDirectory;
    
    /**
     * CNAI Export file.
     * 
     * @since 1.0.0
     */
    static String cnaiExportFile;
    
    /**
     * Domain print writers.
     *
     * @since 1.0.0
     */
    static Map<String, PrintWriter> domainPWMap 
            = new LinkedHashMap<String, PrintWriter>();

    /**
     * Mark which domain parameter headers have been added to the csv file.
     * 
     * @since 1.0.0
     */
    static Map<String, Boolean> domainHeaderAdded 
            = new LinkedHashMap<String, Boolean>();
    
    static String dataSource;
    static String fileName;
    static int parserState = ParserStates.EXTRACTING_VALUES;
    static String baseFileName;
    static String dataFile;
    static String parameterFile = null;
    
    /**
     * The list of parameters to extract for each domain.
     * 
     * @since 1.0.0
     */
    static Map<String,Stack> domainColumnHeaders 
            = new LinkedHashMap<String, Stack>();
    
    public static void setFileName( String fileName ){
        //this.fileName = fileName;
    }
    
    public static void main( String[] args )
    {
        try{        
            //show help
            if( (args.length != 2 && args.length != 3) || (args.length == 1 && args[0] == "-h")){
                showHelp();
                System.exit(1);
            }

            //Get bulk CM XML file to parse.
            String filename = args[0];
            outputDirectory = args[1];

            //Confirm that the output directory is a directory and has write 
            //privileges
            File fOutputDir = new File(outputDirectory);
            if(!fOutputDir.isDirectory()) {
                System.err.println("ERROR: The specified output directory is not a directory!.");
                System.exit(1);
            }

            if(!fOutputDir.canWrite()){
                System.err.println("ERROR: Cannot write to output directory!");
                System.exit(1);            
            }
            
            if(  args.length == 3  ){
                File f = new File(args[2]);
                if(f.isFile()){
                   parameterFile = args[2];
                   getParametersToExtract(parameterFile);
                }
            }
            
            //Expiry check 
            //@Remove this 
            /**
            Date expiryDate =  new GregorianCalendar(2018, Calendar.FEBRUARY, 01).getTime();
            Date todayDate = new Date();  
            //System.out.println(todayDate);
            //System.out.println(expiryDate);
            if(todayDate.after(expiryDate) ) {
                System.out.println("Parser has expired. Please request new version from www.telecomhall.net");
                System.exit(1);
            }
            **/
            
            cnaiExportFile = getFileBasename(filename);

            
            dataSource = filename;
            processFileOrDirectory(filename);
            
//            BufferedReader br = new BufferedReader(new FileReader(filename));
//            for(String line; (line = br.readLine()) != null; ) {
//                processLine(line);
//            }

        }catch(Exception e){
            System.err.println("ERROR:" + e.getMessage());
            e.printStackTrace();
            System.exit(1);  
        }
            
        printExecutionTime();
        closeDomainPWMap();
    }
    
    /**
     * Extract parameter list from  parameter file
     * 
     * @param filename 
     */
    public static void getParametersToExtract(String filename) throws FileNotFoundException, IOException{
        BufferedReader br = new BufferedReader(new FileReader(filename));
        for(String line; (line = br.readLine()) != null; ) {
           String [] moAndParameters =  line.split(":");
           String mo = moAndParameters[0];
           String [] parameters = moAndParameters[1].split(",");
           
           Stack parameterStack = new Stack();
           for(int i =0; i < parameters.length; i++){
               parameterStack.push(parameters[i]);
           }
            domainColumnHeaders.put(mo, parameterStack);
            //domainHeaderAdded.put(mo, Boolean.TRUE);
        }
        
        parserState = ParserStates.EXTRACTING_VALUES;
    }
    
    public static void parse(String inputFilename) throws FileNotFoundException, IOException{
        BufferedReader br = new BufferedReader(new FileReader(inputFilename));
        for(String line; (line = br.readLine()) != null; ) {
            processLine(line);
        }
    }
    public static void processFileOrDirectory(String inputPath)
            throws FileNotFoundException, IOException {
        //this.dataFILe;
        Path file = Paths.get(dataSource);
        boolean isRegularExecutableFile = Files.isRegularFile(file)
                & Files.isReadable(file);

        boolean isReadableDirectory = Files.isDirectory(file)
                & Files.isReadable(file);

        if (isRegularExecutableFile) {
            setFileName(dataSource);
            dataFile = dataSource;
            baseFileName =  getFileBasename(dataFile);
            cnaiExportFile = baseFileName;
            if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                System.out.print("Extracting parameters from " + baseFileName + "...");
            }else{
                System.out.print("Parsing " + baseFileName + "...");
            }
            parse(dataSource);
            if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                 System.out.println("Done.");
            }else{
                System.out.println("Done.");
               //System.out.println(this.baseFileName + " successfully parsed.\n");
            }
        }

        if (isReadableDirectory) {

            File directory = new File(dataSource);

            //get all the files from a directory
            File[] fList = directory.listFiles();

            for (File f : fList) {
                setFileName(f.getAbsolutePath());
                dataFile = f.getAbsolutePath();
                try {
                    baseFileName =  getFileBasename(f.getAbsolutePath());
                    cnaiExportFile = baseFileName;
                    if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                        System.out.print("Extracting parameters from " + baseFileName + "...");
                    }else{
                        System.out.print("Parsing " + baseFileName + "...");
                    }
                    
                    //Parse
                    parse(f.getAbsolutePath());
                    if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                         System.out.println("Done.");
                    }else{
                        System.out.println("Done.");
                        //System.out.println(this.baseFileName + " successfully parsed.\n");
                    }
                   
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.out.println("Skipping file: " + baseFileName + "\n");
                }
            }
        }

    }
    
    /**
     * Holds the parser logic.
     * 
     * @param line  String
     * @since 1.0.0
     * @version 1.0.0
     */
    static public void processLine(String line){
        //Handle first line
        if(line.contains("..cnai")){
            return;
        }
        
        if(line.contains("..end")){
            return;
        }        
        
        //If a ".set " is encounted, 
        if(line.contains(".set ")){            
            prevSet = set;
            set = line.replace(".set ", "");

            //Write parameter s from previous network entity to domain csv file.
            if( domainParameterList.size() > 1 ){
                
                //Skip if domain is not in the parameter file
                if(parameterFile != null && !domainColumnHeaders.containsKey(domain) ){
                    domainParameterList.clear();
                    return;
                }
                
                String paramNames = "FileName";
                String paramValues = cnaiExportFile;

                //add capabilities
                //When parameter file is present, only add if in parameter list
                if( parameterFile == null || 
                        ( parameterFile != null && domainColumnHeaders.get(domain).contains("capabilities"))){
                    paramNames = paramNames +",capabilities";
                    paramValues = paramValues + "," + capabilities;                      
                }

                //add utctime
                //@TODO: Add mapping from configuration file
                //for now call this varDateTime
                paramNames = paramNames +",varDateTime";
                paramValues = paramValues + "," + creationDateTime;    
                

                //When parameter file is present, only add if in parameter list
                //add Subnetwork
                if( parameterFile == null || 
                        ( parameterFile != null && domainColumnHeaders.get(domain).contains("subnetwork"))){
                    paramNames = paramNames +",subnetwork";
                    paramValues = paramValues + "," + subnetwork;                                
                }
                
                
                //add domain
                if( parameterFile == null || 
                        ( parameterFile != null && domainColumnHeaders.get(domain).contains("domain"))){
                    paramNames = paramNames +",domain";
                    paramValues = paramValues + "," + domain;  
                }
                
                //add set
                if( parameterFile == null || 
                        ( parameterFile != null && domainColumnHeaders.get(domain).contains("set"))){
                    paramNames = paramNames +",set";
                    paramValues = paramValues + "," + prevSet;  
                }
                
                if(domainHeaderAdded.get(domain)== true || parameterFile != null){
                    Stack<String> dk = domainColumnHeaders.get(domain);
                    for(int i=0; i < dk.size(); i++ ){
                        String pName = dk.get(i).toString();
                        String pValue = "";
                        
                        if(pName.equals("set") || pName.equals("domain") || 
                            pName.equals("subnetwork") || pName.equals("capabilities") ) continue;
                        
                        if(domainParameterList.containsKey(pName) ){
                            pValue= toCSVFormat(domainParameterList.get(pName));
                        }
                        
                        
                        paramNames = paramNames + "," + pName;
                        paramValues = paramValues + "," + pValue;                           
                    }
                }else {

                    Iterator <Map.Entry<String,String>> iter 
                            = domainParameterList.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<String, String> dp = iter.next();

                        String pValue = toCSVFormat(dp.getValue());
                        String pName = dp.getKey();
                        
                        paramNames = paramNames + "," + pName;
                        paramValues = paramValues + "," + pValue;
                    }
                }
                
                PrintWriter pw = domainPWMap.get(domain);
                
                //Add domain csv file headers
                //If there is no parameter file added
                if(domainHeaderAdded.get(domain)== false ){
                    
                    //Add the file headers
                    pw.println(paramNames);   
                    
                    //Add parameters under the domain to a map for use as a csv 
                    //header
                    String [] paramArr = paramNames.split(",");
                    Stack paramStack = new Stack();
                    
                    //Collect after FileName,capabilities,utctime,subnetwork,domain
                    for(int i=5; i<paramArr.length;i++){
                        paramStack.push(paramArr[i]);
                    }
                    
                    if(parameterFile == null){
                        domainColumnHeaders.put(domain,paramStack);
                    }
                    
                    
                    
                    //Mark the headers as added
                    domainHeaderAdded.put(domain,true);
                    
                }
               
                //Add the parameter values
                pw.println(paramValues);   
                
                //clear the domainParameterList 
                domainParameterList.clear();

            }
            return;
        }
        
        //Get domain
        if(line.contains(".domain")){
            
            //Get domain name
            domain = line.replace(".domain ", "");
            
            //Skip the rest if the domain print writer has already been added.
            if( domainPWMap.containsKey(domain) ){
                return;
            }
        
            //Create domain print writerr
            String domainFile = outputDirectory + File.separatorChar + domain + ".csv";
            try {
                if( parameterFile == null || (parameterFile != null && domainColumnHeaders.containsKey(domain))){
                    domainPWMap.put(domain, new PrintWriter(new File(domainFile)));
                }
                
            } catch (FileNotFoundException e) {
                //@TODO: Add logger
                System.err.println(e.getMessage());
                System.exit(1);
            }
            
            //Mark domain header as not yet added
            if(!domainHeaderAdded.containsKey(domain)){
                domainHeaderAdded.put(domain, false);
            }
            return;
        }
        
        //Get capabilities
        if(line.contains("..capabilities")){
            capabilities = line.replace("..capabilities ", "");       
            return;
        }

        //Get subnetwork
        if(line.contains(".subnetwork")){
            subnetwork = line.replace(".subnetwork ", "");            
            return;
        }

        //Get generation date and time
        if(line.contains(".utctime")){
            creationDateTime = line.replace(".utctime ", "");            
            return;
        }
        
        //Start collecting parameters and their values
        String[] paramValuePair = line.split("=",2);

        if ( paramValuePair.length != 2 ){
            System.out.println("line:" + line );
            return;
        }
        
        if(paramValuePair[0].equals("set")){
            System.out.println(line);
        }
        domainParameterList.put(paramValuePair[0], paramValuePair[1]);
    }
    /**
     * Show parser help.
     * 
     * @since 1.0.0
     * @version 1.0.0
     */
    static public void showHelp(){
        System.out.println("boda-ericcsoncnaiparser 1.0.0. Copyright (c) 2017 Bodastage(http://www.bodastage.com)");
        System.out.println("Parses Ericsson CNAI CP dumps to csv.");
        System.out.println("Usage: java -jar boda-ericssoncnaiparser.jar <fileToParse.dmp> <outputDirectory> <configFile>");
    }
    
    /**
     * Print parser's execution time.
     * 
     * @since 1.0.0
     */
    static public void printExecutionTime(){
        float runningTime = System.currentTimeMillis() - startTime;
        
        String s = "PARSING COMPLETED:\n";
        s = s + "Total time:";
        
        //Get hours
        if( runningTime > 1000*60*60 ){
            int hrs = (int) Math.floor(runningTime/(1000*60*60));
            s = s + hrs + " hours ";
            runningTime = runningTime - (hrs*1000*60*60);
        }
        
        //Get minutes
        if(runningTime > 1000*60){
            int mins = (int) Math.floor(runningTime/(1000*60));
            s = s + mins + " minutes ";
            runningTime = runningTime - (mins*1000*60);
        }
        
        //Get seconds
        if(runningTime > 1000){
            int secs = (int) Math.floor(runningTime/(1000));
            s = s + secs + " seconds ";
            runningTime = runningTime - (secs/1000);
        }
        
        //Get milliseconds
        if(runningTime > 0 ){
            int msecs = (int) Math.floor(runningTime/(1000));
            s = s + msecs + " milliseconds ";
            runningTime = runningTime - (msecs/1000);
        }

        System.out.println(s);
    }
    
    /**
     * Process given string into a format acceptable for CSV format.
     *
     * @since 1.0.0
     * @param s String
     * @return String Formated version of input string
     */
    public static String toCSVFormat(String s) {
        //First remove leading and trailing double quotes if any
        s = s.replaceAll("^\"","");
        s = s.replaceAll("\"$","");
        
        String csvValue = s;
        
        //Check if value contains comma
        if (s.contains(",")) {
            csvValue = "\"" + s + "\"";
        }

        if (s.contains("\"")) {
            csvValue = "\"" + s.replace("\"", "\"\"") + "\"";
        }

        return csvValue;
    }
    
    /**
     * Close file print writers.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    public static void closeDomainPWMap() {
        Iterator<Map.Entry<String, PrintWriter>> iter
                = domainPWMap.entrySet().iterator();
        while (iter.hasNext()) {
            iter.next().getValue().close();
        }
        domainPWMap.clear();
        
    }
    
    /**
     * Get file base name.
     * 
     * @since 1.0.0
     */
    static public String getFileBasename(String filename){
        try{
            return new File(filename).getName();
        }catch(Exception e ){
            return filename;
        }
    }    
}
