package org.usfirst.frc.team3373.robot;
import java.util.Comparator;
import java.util.Vector;


import com.ni.vision.NIVision;
import com.ni.vision.NIVision.Image;
import com.ni.vision.NIVision.ImageType;

import edu.wpi.first.wpilibj.image.HSLImage;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.vision.AxisCamera;

public class HawkVision {	

	public class ParticleReport implements Comparator<ParticleReport>, Comparable<ParticleReport>{
		double PercentAreaToImageArea;
		double Area;
		double BoundingRectLeft;
		double BoundingRectTop;
		double BoundingRectRight;
		double BoundingRectBottom;
		
		public int compareTo(ParticleReport r)
		{
			return (int)(r.Area - this.Area);
		}
		
		public int compare(ParticleReport r1, ParticleReport r2)
		{
			return (int)(r1.Area - r2.Area);
		}
	};
	public class Scores {
		double Area;
		double Aspect;
	};

	//Structure to represent the scores for the various tests used for target identification

	double ratioToScore(double ratio)
	{
		return (Math.max(0, Math.min(100*(1-Math.abs(1-ratio)), 100)));
	}

	double AreaScore(ParticleReport report)
	{
		double boundingArea = (report.BoundingRectBottom - report.BoundingRectTop) * (report.BoundingRectRight - report.BoundingRectLeft);
		//Tape is 7" edge so 49" bounding rect. With 2" wide tape it covers 24" of the rect.
		return ratioToScore((49/24)*report.Area/boundingArea);
	}

	/**
	 * Method to score if the aspect ratio of the particle appears to match the retro-reflective target. Target is 7"x7" so aspect should be 1
	 */
	double AspectScore(ParticleReport report)
	{
		return ratioToScore(((report.BoundingRectRight-report.BoundingRectLeft)/(report.BoundingRectBottom-report.BoundingRectTop)));
	}

	//Images
	Image frame;
	Image binaryFrame;
	int imaqError;

	//Variables
	NIVision.Range GOAL_HUE_RANGE = new NIVision.Range(100, 155);	//Default hue range for goal frame
	NIVision.Range GOAL_SAT_RANGE = new NIVision.Range(0, 255);	//Default saturation range for goal frame
	NIVision.Range GOAL_VAL_RANGE = new NIVision.Range(0, 255);	//Default value range for goal frame
	double AREA_MINIMUM = 0.15; //Default Area minimum for particle as a percentage of total image area
	double LONG_RATIO = 20; //GOAL long side = 26.9 / GOAL height = 12.1 = 2.22
	double SHORT_RATIO = 14; //GOAL short side = 16.9 / GOAL height = 12.1 = 1.4
	double SCORE_MIN = 30.0;  //Minimum score to be considered a GOAL
	double VIEW_ANGLE = 49.4; //View angle for camera
	NIVision.ParticleFilterCriteria2 criteria[] = new NIVision.ParticleFilterCriteria2[1];
	NIVision.ParticleFilterOptions2 filterOptions = new NIVision.ParticleFilterOptions2(0,0,1,1);
	AxisCamera visionCamera = new AxisCamera("10.33.73.29");
	Scores scores = new Scores();
	boolean isGoal;
	
	
	//creates unfiltered images
	
	
	public void getVisionImage(){  //this method calls one image and then continually replaces it
		HSLImage image;
		frame = NIVision.imaqCreateImage(ImageType.IMAGE_RGB, 0);
		binaryFrame = NIVision.imaqCreateImage(ImageType.IMAGE_U8, 0);
		criteria[0] = new NIVision.ParticleFilterCriteria2(NIVision.MeasurementType.MT_AREA_BY_IMAGE_AREA, AREA_MINIMUM, 100.0, 0, 0);
		try {
        	image = visionCamera.getImage();
        	System.out.println("got Image");
        	image.write("/home/lvuser/image.jpg");
        	}catch (Exception e){
        		System.out.println("exception occured: " + e);
        	}
		NIVision.imaqReadFile(frame, "/home/lvuser/image.jpg");

		//sets ranges to filter on
		GOAL_HUE_RANGE.minValue = 95;
		GOAL_HUE_RANGE.maxValue = 140;
		GOAL_SAT_RANGE.minValue = 215;
		GOAL_SAT_RANGE.maxValue = 255;
		GOAL_VAL_RANGE.minValue = 215;
		GOAL_VAL_RANGE.maxValue = 255;

		//Threshold the image looking for green (GOAL color)
		NIVision.imaqColorThreshold(binaryFrame, frame, 255, NIVision.ColorMode.HSV, GOAL_HUE_RANGE, GOAL_SAT_RANGE, GOAL_VAL_RANGE);

		//Send particle count to dashboard
		int numParticles = NIVision.imaqCountParticles(binaryFrame, 1);
		System.out.println("Masked particles" + numParticles);

		//Send masked image to dashboard to assist in tweaking mask.
		//CameraServer.getInstance().setImage(binaryFrame);

		//filter out small particles
		NIVision.imaqWriteJPEGFile(binaryFrame, "/home/lvuser/filterImage.jpg", 255, null);
		float areaMin = (float) 0.15;
		criteria[0].lower = areaMin;
		imaqError = NIVision.imaqParticleFilter4(binaryFrame, binaryFrame, criteria, filterOptions, null);

		//Send particle count after filtering to dashboard
		numParticles = NIVision.imaqCountParticles(binaryFrame, 1);
		System.out.println(numParticles + " #Particles");
		if(numParticles > 0){
		Vector<ParticleReport> particles = new Vector<ParticleReport>();
		for(int particleIndex = 0; particleIndex < numParticles; particleIndex++)
			//Bounding rectangle VVV
		{
			ParticleReport par = new ParticleReport();
			par.PercentAreaToImageArea = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_AREA_BY_IMAGE_AREA);
			par.Area = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_AREA);
			par.BoundingRectTop = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_TOP);
			par.BoundingRectLeft = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_LEFT);
			par.BoundingRectBottom = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_BOTTOM);
			par.BoundingRectRight = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_RIGHT);
			particles.add(par);
		}
		particles.sort(null);
		scores.Aspect = AspectScore(particles.elementAt(0));
		System.out.println("Aspect " + scores.Aspect);
		scores.Area = AreaScore(particles.elementAt(0));
		System.out.println("Area " + scores.Area);
		isGoal = scores.Aspect > SCORE_MIN && scores.Area > SCORE_MIN;
	
		}else{
		 isGoal = false;
		}
		System.out.println("Goal? " + isGoal);
		frame.free();
		binaryFrame.free();
	}		
}
