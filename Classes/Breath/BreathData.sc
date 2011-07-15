BreathData {
	classvar clstrdict, clstrpoints, spkrs, distances, <numclusters;

/* 
CLUSTER INDEXING LAYOUT

0		1		2
		3		4		5
		6		7		8
9		10		11

SPEAKER INDEXING LAYOUT
  *       *               *
	3   0	7  4	     11 8
	2   1	6  5	     10 9
		  * 	      *
			14 12	18 15	
			   13	17 16	19
			      *       *
			   20	26 23	27
			22 21	25 24
		 *	      *
	31 28	35 32	39 36
	30 29	34 33	38 37
  *      *          *
*/

	*new { ^super.new }
	
	*initClass {
		var layers;
		clstrpoints = [		[0,3], // cluster 0
							[1,3], // cluster 1
							[2,3], // etc...
							[1,2],
							[2,2],
							[3,2],
							[1,1],
							[2,1],
							[3,1],
							[0,0],
							[1,0],
							[2,0]		];
							
		spkrs = [		[ 0, 1, 2, 3 ],
					[ 4, 5, 6, 7 ],
					[ 8, 9, 10, 11 ],
					[ 12, 13, 14, nil ],
					[ 15, 16, 17, 18 ],
					[ 19, nil, nil, nil ],
					[ 20, 21, 22, nil ],
					[ 23, 24, 25, 26 ],
					[ 27, nil, nil, nil ],
					[ 28, 29, 30, 31 ],
					[ 32, 33, 34, 35 ],
					[ 36, 37, 38, 39 ]		];
		
		numclusters = clstrpoints.size;
		
			// cluster dictionary, primary key is the cluster number from 0
			// the value is another dictionary containing the point 
			// in space on a grid, and the speakers of each cluster
		clstrdict = Dictionary.new;
		clstrpoints.do( { | me, dex |
			clstrdict.add( dex -> 
				Dictionary.new
					.add( 'pnt' -> Point.new(me[0],me[1]) )
					.add( 'speakers' -> spkrs[dex] )
				)
		});
		
		distances = Array.newClear(12);
		distances = distances.collect{ | foo, i | 
			var thisPoint, distArr;
				thisPoint = clstrdict.at(i).at('pnt');
				distArr = Array.newClear(12);
				distArr.collect{ | me, j | 
						thisPoint.dist( clstrdict.at(j).at('pnt') );
					};
			};
			
			// helper function
			// pairing distances with clusters, run this function for each cluster
		layers = { | clusternum | // cluster is a cluster index 0->11
			var dists, dist_clstrDict, temp;
			dist_clstrDict = Dictionary.new;
			dists = distances[clusternum];
			dists.do( { |me, i|
					// possibly add more conditions here to reduce # of layers
					dist_clstrDict.includesKey.if( {
						//"unique".postln;
						dist_clstrDict.add(me -> i.asArray); // add the distance -> clusternum
						},{
						//"not unique".postln;
							// make a temporary Dict that is the current one plus the new cluster
						temp = dist_clstrDict.at(me).add(i);
							// replace the current key value pair with the  updated one
						dist_clstrDict.add(me -> temp);
						})
				});
				// return the dictionary as sorted array:  [distance, [cluster, indexes ]]
			dist_clstrDict.asSortedArray;
		};
		
		numclusters.do({ | i |
				// add a key 'dist_clstrs' with an array [dist, [layer, clusters]]
			clstrdict.at( i )
				.add( 'dist_clstrs' -> layers.value( i ) );
			})
	}
	
	clusterSpeakers	{ | clusternum |
		^clstrdict.at(clusternum).at('speakers')
	}
	
		// get the clusters for each layer, starting with layer 0, the source cluster
	clustersByLayer	{ | clusternum |
		var dist_clusters, clusters;
		//clstrdict.at( clusternum ).at( 'dist_clstrs' ).postln;
		dist_clusters = clstrdict.at( clusternum ).at( 'dist_clstrs' );
		clusters = dist_clusters.collect({ |me, i| me.drop(1).flat });
		^clusters;
	}
	
		// number of virtual busses this cluster writes to -- number of concentric layers
	numVirtBusses	{ | clusternum |
		^clstrdict.at( clusternum ).at( 'dist_clstrs' ).size;
		//^this.getLayers( clusternum ).size;
	}	
	
	getLayers	 { | clusternum |
		^clstrdict.at( clusternum ).at( 'dist_clstrs' )
	}
	
	getLayersDists { | clusternum |
		^clstrdict.at( clusternum ).at( 'dist_clstrs' )
	}
	
	panEnv	{ | clusternum |
		var dists;
		dists = this.getLayers( clusternum ).collect({ | me | me[0] });
		^InterplEnv( dists.normalize, (1/(dists.size)).dup(dists.size-1), \lin )
	}
	
	totalVirtBusses {
		var sum=0;
		numclusters.do({ |i| 
			sum = sum + clstrdict.at( i ).at( 'dist_clstrs' ).size;
			});
		^sum
	}
		// get the distance from one cluster to every other on
		// starting with distance to cluster 0 and up, including itself
	distToOtherClusters { | clusternum |
		^distances[clusternum]
	}

}