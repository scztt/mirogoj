AtsSnapShot {
	var <freqs, <amps, <thresh, myAtsFile, <server, <path;
	var <origAmps, <origFreqs;
	
	*new { arg path, pointer = 0.5, framesToAverage = 3, ampThresh = -60.dbamp, server;
		^super.new.init(path, server, pointer, framesToAverage, ampThresh)
		}
	
	init { | argpath, argserver, pointer, framesToAverage, ampThresh |
		path = argpath;
		server = argserver ?? { Server.default };
		myAtsFile = AtsFile.new(path, server);
		this.farm(pointer, framesToAverage, ampThresh);
	}
	
		// ampthresh expects values from 0 - 1, ie. -20.dbamp
	farm { arg  pointer, framesToAverage, ampThresh;
		var startFrame, avgAmps, avgFreqs, origSnapAmps;
		thresh = ampThresh;
		startFrame = (myAtsFile.numFrames * pointer).round(1);
		startFrame = startFrame - framesToAverage.half.floor;
		freqs = [];
		amps = [];
		origAmps = [];
		(startFrame < 0).if({
			startFrame = 0; 
			"first frame to average is ZERO".postln;
			"you'll likely get zeros or nothing at all if your framesToAverage is small".postln
			});
		((startFrame + framesToAverage) > (myAtsFile.numFrames - 1)).if({
			startFrame = (myAtsFile.numFrames - 1 - framesToAverage); 
			"frame averager reached the last frame".postln;
			"you'll likely get zeros or nothing at all if your framesToAverage is small".postln
			});
		avgAmps = 0;
		avgFreqs = 0;
		
		(framesToAverage > 1).if({
			framesToAverage.do({arg i;
				avgAmps = avgAmps + myAtsFile.getFrameAmp(startFrame + i);
				avgFreqs = avgFreqs + myAtsFile.getFrameFreq(startFrame + i);
				});
			},
			{
			avgAmps = myAtsFile.getFrameAmp(startFrame);
			avgFreqs = myAtsFile.getFrameFreq(startFrame);
			}
		);

		#avgAmps, avgFreqs = [avgAmps,avgFreqs] / framesToAverage;
			// averaged amp array is normalized before checking against ampThresh
		origSnapAmps = avgAmps; // all of the original amps, before normalizing and threshold operation
		avgAmps = avgAmps.normalize;
		
		myAtsFile.numPartials.do({arg i;
			(avgAmps[i] > ampThresh).if({
				freqs = freqs.add(avgFreqs[i]);
				amps = amps.add(avgAmps[i]);		
				origAmps = origAmps.add(origSnapAmps[i]);
				origFreqs = origFreqs.add(avgFreqs[i]);
				})
		});
		}
	
	test { | bw=0.005, mul = 1.5, dur = 5, server |
		var testGroup, srcBus, thisserver, note, thistest;
		thisserver = server ?? {Server.default};
		srcBus = CtkAudio.new(1, server: thisserver);				testGroup = CtkGroup.play( server: thisserver, addAction:\tail );
		//server.sendMsg(\g_new, testGroup = server.nextNodeID, 0, 1);
		//testGroup.postln;
			// create a noise source
		note = CtkNoteObject(
				SynthDef( \noiseTest, { arg out;
				Out.ar(out,
					InGrain.ar(Impulse.kr(MouseX.kr(10, 150)), 
						MouseX.kr(10, 100).reciprocal,
						WhiteNoise.ar(0.5))
					) }) );
		thistest = note.new(addAction: \head, target: testGroup).out_(srcBus).play;

		this.numSnapPartials.do({ | i |
			VoicePartial.new(server, testGroup, srcBus, 0, 
				Env([freqs[i], freqs[i]],[dur], 0, 1),
				Env([-400.dbamp, amps[i]],[dur/4], 5.5, 1), 
				Env([bw, bw],[dur], 0, 1), 
				1.0).play;
			});
			
		{
			testGroup.deepFree;
	//			server.sendMsg(\g_freeAll, testGroup); 
	//			server.sendMsg(\n_free, testGroup); 
			srcBus.free; 	// this doesn't work?
		}.defer(dur); // use SystemClock.sched?
	}
	
	snapFreqs {
		^freqs
	}
	
	snapAmps {
		^amps
	}
	
	numSnapPartials {
		^freqs.size
	}
	
	isolatePartials { | first, last |
		var rangedFreqs, rangedAmps;
		rangedFreqs = freqs.copyRange(first, last);
		rangedAmps = amps.copyRange(first, last);
		^[rangedFreqs, rangedAmps]
	}
	
	isolatePartialFreqs { | first, last |
		^freqs.copyRange(first, last);
	}

	isolatePartialAmps { | first, last |
		^amps.copyRange(first, last);
	}
	
	normalizeAmps { | normValue |
		^(amps.normalize * normValue)
	}
	
	transpose { | transposeVal, semitone=true |
		var tempfreqs;
		(semitone).if({
				tempfreqs = freqs * transposeVal.midiratio;
				tempfreqs.do({ | freq |
				( (freq < 20) || (freq > 20000) ).if(
						{ "you shifted too much!!".postln; ^nil },
						{ freqs = tempfreqs }
					)
				});
			},{
				tempfreqs = freqs * transposeVal;
				tempfreqs.do({ | freq |
				( (freq < 20) || (freq > 20000) ).if(
						{ "you shifted too much!!".postln; ^nil },
						{ freqs = tempfreqs }
					)
				});
			});

	}
	
	transposeAndCopy { | transposeVal, semitone = true |
		var newSnapShot;
		newSnapShot = this.deepCopy;
		newSnapShot.transpose(transposeVal, semitone );
		newSnapShot.freqs.do({ | freq |
			( (freq < 20) || (freq > 20000) ).if(
				{ "you shifted too much!!".postln; ^nil },
				{ ^newSnapShot }
			)
		});
	}
	
	shift { | shiftHz |
		var tempfreqs;
		//^freqs = freqs + shiftHz;	//^freqs = freqs + shiftHz; <--for permanent manipulations
		tempfreqs = freqs + shiftHz;
		tempfreqs.do({ | freq |
			( (freq < 20) || (freq > 20000) ).if(
					{ "you shifted too much!!".postln; ^nil },
					{ freqs = tempfreqs }
				)
		});
	}
	
	shiftAndCopy { | shiftHz |
		var newSnapShot;
		newSnapShot = this.deepCopy;
		newSnapShot.shift( shiftHz );
		newSnapShot.freqs.do({ | freq |
			( (freq < 20) || (freq > 20000) ).if(
				{ "you shifted too much!!".postln; ^nil },
				{ ^newSnapShot }
			)
		});
	}
		// reset this snapshot
	reset {
		freqs = origFreqs;
		amps = origAmps;
	}
/*	-- revise with curve
	// make quieter partials louder, 1 is one normalized sqrt, 2 does the operation twice, etc.
	compressAmps {arg scaler; 
		var compressedAmps;
		compressedAmps = amps;
		scaler.do({
			compressedAmps = compressedAmps.sqrt.normalize});
		^compressedAmps;
		}
	
	// make quieter partials quieter
	expandAmps {arg pow;
		^amps.pow(pow).normalize;
		}
		
	expandClipPartials {arg pow, thresh;
		var testAmps, returnFreqs, returnAmps;
		testAmps = amps.pow(pow).normalize;
		testAmps.do({arg testamp, i;
			(testamp > thresh).if({
				returnAmps = returnAmps.add(testamp);
				returnFreqs = returnFreqs.add(freqs[i]);
				});
			});
		^[returnFreqs, returnAmps]
		}
*/

		// format for Voice class to create partials
	freqAmpPairs {	
		var pairs;
		pairs = [];
		this.numSnapPartials.do({ | i |
			pairs = pairs.add([freqs[i], amps[i]])
			});
		^pairs
	}
	
	postData {
		"This Snapshot:".postln;
		("numPartials: " ++ freqs.size).postln;
		("minFreq: " ++ freqs[0]).postln;
		("maxFreq: " ++ freqs[freqs.size - 1]).postln;
		("minAmp (dB): " ++ amps.sort[0].ampdb).postln;
		("maxAmp (dB): " ++ amps.sort.reverse[0].ampdb).postln;
	}
}
	
//	ssID {
//		// GIVE SNAPSHOTS AN ID
//	}